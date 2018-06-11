package org.clyze.doop.utils

import groovy.transform.TupleConstructor
import org.apache.commons.logging.Log
import org.clyze.doop.core.DoopAnalysisFactory
import org.clyze.utils.CheckSum
import org.clyze.utils.Executor
import org.clyze.utils.Helper

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.apache.commons.io.FileUtils.deleteQuietly

@TupleConstructor
class SouffleScript {

	File scriptFile
	File inDir
	File outDir
	File cacheDir
	Executor executor
	Log logger

	long compilationTime = 0L
	long executionTime = 0L

	def run(int jobs, boolean profile = false, boolean debug = false, boolean removeContext = false) {
		def origFile = scriptFile
		def scriptFile = File.createTempFile("gen_", ".dl", outDir)
		executor.execute(["cpp", "-P", origFile, scriptFile].collect { it as String }) { logger.info it }

		def c1 = CheckSum.checksum(scriptFile, DoopAnalysisFactory.HASH_ALGO)
		def c2 = c1 + profile.toString()
		def checksum = CheckSum.checksum(c2, DoopAnalysisFactory.HASH_ALGO)
		def cacheFile = new File(cacheDir, checksum)

		if (!cacheFile.exists() || debug) {

			if (removeContext) {
				def backupFile = new File("${scriptFile}.backup")
				Files.copy(scriptFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
				ContextRemover.removeContexts(backupFile, scriptFile)
			}

			def executable = new File(outDir, "exe")
			def compilationCommand = ['souffle', '-c', '-o', executable, scriptFile]
			if (profile)
				compilationCommand << ("-p${outDir}/profile.txt")
			if (debug)
				compilationCommand << ("-r${outDir}/report.html")

			logger.info "Compiling Datalog to C++ program and executable"
			logger.debug "Compilation command: $compilationCommand"

			def ignoreCounter = 0
			compilationTime = Helper.timing {
				executor.execute(compilationCommand.collect { it as String }) { String line ->
					if (ignoreCounter != 0) ignoreCounter--
					else if (line.startsWith("Warning: No rules/facts defined for relation") ||
							line.startsWith("Warning: Deprecated output qualifier was used")) {
						logger.info line
						ignoreCounter = 2
					} else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
					else logger.info line
				}
			}

			try {
				// COPY_ATTRIBUTES: Keep execute permission
				def options = removeContext ? StandardCopyOption.REPLACE_EXISTING : StandardCopyOption.COPY_ATTRIBUTES
				Files.copy(executable.toPath(), cacheFile.toPath(), options)
			} catch (FileAlreadyExistsException e) {
				// If a cached file is already there, don't overwrite it
				// (it might be used by another analysis), just reuse it.
				logger.info "Copy failed, someone else has already created ${cacheFile.canonicalPath}"
			}

			logger.info "Analysis compilation time (sec): $compilationTime"
			logger.info "Caching analysis executable $checksum in $cacheDir"
		} else {
			logger.info "Using cached analysis executable $checksum from $cacheDir"
		}

		def db = new File(outDir, "database")
		deleteQuietly(db)
		db.mkdirs()

		def executionCommand = [cacheFile, "-j$jobs", "-F$inDir", "-D$db"]
		if (profile)
			executionCommand << ("-p${outDir}/profile.txt")

		logger.debug "Execution command: $executionCommand"
		logger.info "Running analysis"
		executionTime = Helper.timing { executor.execute(executionCommand.collect { it as String }) }
		logger.info "Analysis execution time (sec): $executionTime"

		return [compilationTime, executionTime]
	}
}
