package org.clyze.doop.utils

import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.DoopAnalysisFactory
import org.clyze.utils.CheckSum
import org.clyze.utils.CPreprocessor
import org.clyze.utils.Executor
import org.clyze.utils.Helper

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.apache.commons.io.FileUtils.deleteQuietly

@TupleConstructor
@Log4j
class SouffleScript {

	static final String EXE_NAME = "exe"

	Executor executor
	boolean viaDDlog
	long compilationTime = 0L
	long executionTime = 0L
	File scriptFile = null

	void preprocess(File output, File input) {
		CPreprocessor cpp = new CPreprocessor(executor)
		cpp.disableLineMarkers().enableLogOutput()
		cpp.preprocessIfExists(output.canonicalPath, input.canonicalPath)
	}

	File compile(File origScriptFile, File outDir, File cacheDir,
                 boolean profile = false, boolean debug = false,
                 boolean provenance = false, boolean liveProf = false,
                 boolean forceRecompile = true, boolean removeContext = false, boolean useFunctors = false) {

		scriptFile = File.createTempFile("gen_", ".dl", outDir)
		preprocess(scriptFile, origScriptFile)

		if (viaDDlog) {
			log.info "Compilation will happen at the 'run' step."
			return
		}

		if (useFunctors) {
			detectFunctors(outDir)
		}

		def c1 = CheckSum.checksum(scriptFile, DoopAnalysisFactory.HASH_ALGO)
		def c2 = c1 + profile.toString() + provenance.toString() + liveProf.toString()
		def checksum = CheckSum.checksum(c2, DoopAnalysisFactory.HASH_ALGO)
		def cacheFile = new File(cacheDir, checksum)

		if (!cacheFile.exists() || debug || forceRecompile) {

			if (removeContext) {
				def backupFile = new File("${scriptFile}.backup")
				Files.copy(scriptFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
				ContextRemover.removeContexts(backupFile, scriptFile)
			}

			def executable = new File(outDir, EXE_NAME)
			def compilationCommand = "souffle -c -o $executable $scriptFile".split().toList()
			if (profile)
				compilationCommand << ("-p${outDir}/profile.txt" as String)
			if (debug)
				compilationCommand << ("-r${outDir}/report.html" as String)
			if (provenance)
				// Another possible mode is 'explore' but does not support history.
				compilationCommand << ("--provenance=explain" as String)
			if (liveProf)
				compilationCommand << ("--live-profile" as String)

			log.info "Compiling Datalog to C++ program and executable"
			log.debug "Compilation command: $compilationCommand"

			def ignoreCounter = 0
			compilationTime = Helper.timing {
				Path tmpFile = Files.createTempFile("", "")
				executor.executeWithRedirectedOutput(compilationCommand, tmpFile.toFile()) { String line ->
					if (ignoreCounter != 0) ignoreCounter--
					else if (line.startsWith("Warning: No rules/facts defined for relation") ||
							line.startsWith("Warning: Deprecated output qualifier was used")) {
						log.info line
						ignoreCounter = 2
					} else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
					else log.info line
				}
				Files.delete(tmpFile)
			}

			try {
				// COPY_ATTRIBUTES: Keep execute permission
				Files.copy(executable.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
			} catch (FileAlreadyExistsException e) {
				// If a cached file is already there, don't overwrite it
				// (it might be used by another analysis), just reuse it.
				log.info (e.message)
			}

			log.info "Analysis compilation time (sec): $compilationTime"
			log.info "Caching analysis executable $checksum in $cacheDir"
		} else {
			log.info "Using cached analysis executable $checksum from $cacheDir"
		}
		return cacheFile
	}

	def run(File cacheFile, File factsDir, File outDir,
	        int jobs, long monitoringInterval, Closure monitorClosure,
			boolean provenance = false, boolean liveProf = false,
			boolean profile = false) {

		def db = new File(outDir, "database")
		deleteQuietly(db)
		db.mkdirs()

		if (viaDDlog) {
			try {
				runWithDDlog(factsDir, outDir, db, jobs)
				return
			} catch (ex) {
				throw new DoopErrorCodeException(25, ex)
			}
		}

		def executionCommand = "$cacheFile -j$jobs -F${factsDir.canonicalPath} -D${db.canonicalPath}".split().toList()
		if (profile)
			executionCommand << ("-p${outDir}/profile.txt" as String)

		def cmd = executionCommand.join(" ")
		if (provenance || liveProf) {
			def mode = provenance ? "provenance" : (liveProf ? "live profiling" : "unknown")
			println "This process will now exit, run this command to run the analysis with the requested interactive mode (${mode}): rlwrap ${cmd}"
			throw new DoopErrorCodeException(22)
		}

		log.debug "Execution command: ${cmd}"
		log.info "Running analysis"
		executionTime = Helper.timing {
			executor.enableMonitor(monitoringInterval, monitorClosure).execute(executionCommand).disableMonitor()
		}
		log.info "Analysis execution time (sec): $executionTime"

		return [compilationTime, executionTime]
	}

    def interpretScript(File origScriptFile, File outDir, File factsDir,
                   boolean profile = false, boolean debug = false,
                   boolean removeContext = false) {

        def scriptFile = File.createTempFile("gen_", ".dl", outDir)
		preprocess(scriptFile, origScriptFile)

        def db = new File(outDir, "database")
        deleteQuietly(db)
        db.mkdirs()

        if (removeContext) {
            def backupFile = new File("${scriptFile}.backup")
            Files.copy(scriptFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            ContextRemover.removeContexts(backupFile, scriptFile)
        }

        def interpretationCommand = "souffle  $scriptFile -F$factsDir -D$db".split().toList()
        if (profile)
            interpretationCommand<< ("-p${outDir}/profile.txt" as String)
        if (debug)
            interpretationCommand << ("-r${outDir}/report.html" as String)

        log.info "Interpreting analysis script"
        log.debug "Interpretation command: $interpretationCommand"

        def ignoreCounter = 0
        executionTime = Helper.timing {
            Path tmpFile = Files.createTempFile("", "")
            executor.executeWithRedirectedOutput(interpretationCommand, tmpFile.toFile()) { String line ->
                if (ignoreCounter != 0) ignoreCounter--
                else if (line.startsWith("Warning: No rules/facts defined for relation") ||
                        line.startsWith("Warning: Deprecated output qualifier was used")) {
                    log.info line
                    ignoreCounter = 2
                } else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
                else log.info line
            }
            Files.delete(tmpFile)
        }

        log.info "Analysis execution time (sec): $executionTime"
        return [compilationTime, executionTime]
    }

	/**
	 * Execute the 'run' phase using the DDLog Souffle converter.
	 *
	 * @param factsDir	 the facts directory
	 * @param outDir	 the output directory
	 * @param db		 the database directory
	 * @param jobs		 the number of jobs to use when compiling/running the analysis
	 */
	private void runWithDDlog(def factsDir, def outDir, def db, def jobs) {
		String DDLOG_DIR = "DDLOG_DIR"
		String ddlogDir = System.getenv(DDLOG_DIR)
		if (!ddlogDir) {
			throw new DoopErrorCodeException(24, new RuntimeException("Environment variable ${DDLOG_DIR} is empty."))
		} else {
			log.debug "Using DDlog in ${ddlogDir}"
		}
		String doopHome = System.getenv("DOOP_HOME")
		if (!doopHome) {
			throw new DoopErrorCodeException(24, new RuntimeException("Environment variable DOOP_HOME is empty."))
		} else {
			log.debug "Using Doop home: ${doopHome}"
		}

		// Step 1: Convert facts and analysis logic.
		String ddlogSouffleDir = "${ddlogDir}/tools"
		["souffle-grammar.pg", "souffle-converter.py"].each {
			File from = new File(ddlogSouffleDir, it)
			File to = new File(factsDir, it)
			log.debug "COPY: ${from.canonicalPath} -> ${to.canonicalPath}"
			Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
		}
		if (!scriptFile) {
			throw new RuntimeException("Error: no script file, compile() must precede run().")
		}
		String convertedLogicName = "converted_logic" as String
		String convertedLogicPrefix = "${outDir}/${convertedLogicName}" as String
		String convertedLogic = "${convertedLogicPrefix}.dl" as String
		String dat = "${factsDir}/dat" as String
		def cmdConvert = ["${doopHome}/bin/run-in-dir.sh" as String,
						  factsDir.canonicalPath,
						  "${factsDir}/souffle-converter.py" as String,
						  scriptFile.canonicalPath,
						  convertedLogic,
						  dat,
						  "${outDir}/log" as String ]
		log.debug "Running conversion command: ${cmdConvert}"
		executeCmd(cmdConvert)

		// Step 2: Compile the analysis.
		def genTime = Helper.timing {
			log.info "Compiling the analysis: code generation..."
			def cmdGenRust = "${doopHome}/bin/run-in-dir.sh ${ddlogDir} stack run -- -i ${convertedLogic} --action=compile -L lib".split().toList()
			executeCmd(cmdGenRust)
		}
		log.info "Time: ${genTime}"
		String buildDir = "${convertedLogicPrefix}_ddlog" as String
		def buildTime = Helper.timing {
			log.info "Compiling the analysis: building (using ${jobs} jobs)..."
			log.debug "Build dir: ${buildDir}"
			def cmdBuildRust = "${doopHome}/bin/run-in-dir.sh ${buildDir} cargo build -j ${jobs} --release".split().toList()
			executeCmd(cmdBuildRust)
		}
		log.info "Time: ${buildTime}"

		// Step 3: Run the analysis.
		log.info "Running the analysis (using ${jobs} jobs)..."
		def runTime = Helper.timing {
			def dump = "${db.canonicalPath}/dump"

			// Hack: use script to get away with redirection.
			def cmdRun = "${doopHome}/bin/run-with-redirection.sh ${dat} ${dump} ${buildDir}/target/release/${convertedLogicName}_cli -w ${jobs}".split().toList()
			executeCmd(cmdRun)
		}
		log.info "Time: ${runTime}"
	}

	/**
	 * Executes a command using a temporary file for its output.
	 *
	 * @param cmd	the command line to invoke
	 */
	void executeCmd(List<String> command) {
		log.debug command
		Path tmpFile = Files.createTempFile("", "")
		executor.executeWithRedirectedOutput(command, tmpFile.toFile()) { println it }
		Files.delete(tmpFile)
	}

	// Detect libfunctors.so and create corresponding symbolic link.
	private void detectFunctors(File outDir) {
		String envVar = "LD_LIBRARY_PATH"
		String ldLibPath = System.getenv(envVar)
		if(ldLibPath != null) {
			def libfunctors = null
			ldLibPath.split(File.pathSeparator).each {
				File f = new File("${it}/libfunctors.so")
				if (f.exists()) {
					libfunctors = f.canonicalPath
				}
			}
			if (libfunctors != null) {
				String libName = "libfunctors.so"
				try {
					Path target = FileSystems.default.getPath(libfunctors)
					Path link = FileSystems.default.getPath(outDir.getAbsolutePath() + File.separator + libName)
					Files.createSymbolicLink(link, target)
					log.debug "Created symbolic link: ${link} -> ${target}"
				} catch (UnsupportedOperationException ignored) {
					log.debug "Filesystem does not support symbolic link for file ${libfunctors}"
					// Fallback (non-portable).
					// executor.execute("ln -s ${libfunctors} libfunctors.so".split().toList()) { log.info it }
				} catch (FileAlreadyExistsException) {
					log.info "Warning: could not create link to ${libName}, file already exists."
				}
			} else {
				log.debug "Warning: no ${libName} in environment variable ${envVar} = '${ldLibPath}'"
			}
		}
	}
}
