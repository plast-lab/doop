package org.clyze.doop.utils

import groovy.util.logging.Log4j
import java.util.regex.Matcher
import org.clyze.utils.Executor

@Log4j
class LBBuilder {

	CPreprocessor cpp
	File outDir
	File script
	PrintWriter writer

	LBBuilder(CPreprocessor cpp, File outDir) {
		this.cpp = cpp
		this.outDir = outDir
		script = File.createTempFile("run", ".lb", outDir)
		writer = script.newPrintWriter()
	}

	LBBuilder echo(String message) { eval("\necho $message") }

	LBBuilder startTimer() { eval("startTimer") }

	LBBuilder elapsedTime() { eval("elapsedTime") }

	LBBuilder transaction() { eval("transaction") }

	LBBuilder timedTransaction(String message) { echo(message).startTimer().transaction() }

	LBBuilder commit() { eval("commit") }

	LBBuilder createDB(String database) { eval("create $database --overwrite --blocks base") }

	LBBuilder openDB(String database) { eval("open $database") }

	LBBuilder addBlock(String logiqlString) { eval("addBlock '$logiqlString'") }

	LBBuilder addBlockFile(String filePath) { eval("addBlock -F $filePath") }

	LBBuilder addBlockFile(String filePath, String blockName) { eval("addBlock -F $filePath -B $blockName") }

	LBBuilder execute(String logiqlString) { eval("exec '$logiqlString'") }

	LBBuilder executeFile(String filePath) { eval("exec -F $filePath") }

	LBBuilder eval(String cmd) {
		writer.write(cmd + "\n")
		return this
	}

	void invoke(String bloxbatch, String bloxOpts, Executor executor) {
		writer.close()
		log.info "Using generated script $script"
		def cmd = [bloxbatch, '-script', script as String]
		if (bloxOpts) cmd += (bloxOpts.split(" ") as List)
		executor.execute(cmd)
	}

	void include(String filePath) {
		def inDir = new File(filePath).parentFile
		File tmpFile = File.createTempFile("tmp", ".lb", outDir)
		tmpFile.deleteOnExit()
		cpp.preprocess(tmpFile.toString(), filePath)
		tmpFile.eachLine { line ->
			Matcher matcher = (line =~ /^(addBlock|exec)[ \t]+-[a-zA-Z][ \t]+(.*\.logic)$/)
			if (matcher.matches()) {
				String inFile = matcher[0][2] as String
				def outFile = inFile.replaceAll(File.separator, "-")
				cpp.preprocess("$outDir/$outFile", "$inDir/$inFile")
			}
			eval(line)
		}
	}
}
