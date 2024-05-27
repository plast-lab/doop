package org.clyze.doop.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.DoopAnalysisFactory
import org.clyze.doop.core.DoopAnalysisFamily
import org.clyze.doop.util.Resource
import org.clyze.utils.CheckSum
import org.clyze.utils.Executor
import org.clyze.utils.Helper
import org.clyze.utils.OS

@Log4j
@CompileStatic
class SouffleScript {

	static final String EXE_NAME = "analysis-binary"
	protected static final String TIME_UTIL = "/usr/bin/time"
	protected static final String CHPST_UTIL = '/usr/bin/chpst'

	Executor executor
	File cacheDir
	long compilationTime = 0L
	long executionTime = 0L
	File scriptFile = null

	SouffleScript(Executor executor, File cacheDir) {
		this.executor = executor
		this.cacheDir = cacheDir
		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}
	}

	static String getExeName() {
		return OS.win ? EXE_NAME + '.exe' : EXE_NAME
	}

	static SouffleScript newScript(Executor executor, File cacheDir, boolean viaDDlog) {
		return viaDDlog ? new DDlog(executor, new File(cacheDir, "ddlog")) : new SouffleScript(executor, cacheDir)
	}

	void setScriptFileViaCPP(File input, File outDir) {
		File output = File.createTempFile("gen_", ".dl", outDir)
		// output.deleteOnExit()
		CPreprocessor cpp = new CPreprocessor(executor)
		cpp.disableLineMarkers().enableLogOutput()
		cpp.preprocessIfExists(output.canonicalPath, input.canonicalPath)
		this.scriptFile = output
	}

	/**
	 * Calculates the checksum of the cached compiled analysis binary.
	 *
	 * @param profile	  profiling mode
	 * @param provenance  provenance mode
	 * @param liveProf	  live profiling mode
	 */
	final String calcChecksum(boolean profile, boolean provenance,
							  boolean liveProf) {
		def c1 = CheckSum.checksum(scriptFile, DoopAnalysisFactory.HASH_ALGO)
		// We also hash the current class name so that subclasses of
		// SouffleScript cache their binaries in different paths.
		def c2 = c1 + profile.toString() + provenance.toString() + liveProf.toString() + getClass().toString()
		return CheckSum.checksum(c2, DoopAnalysisFactory.HASH_ALGO)
	}

	File compile(File origScriptFile, File factsDir, File outDir,
                 SouffleOptions options) {

		setScriptFileViaCPP(origScriptFile, outDir)

		if (options.useFunctors) {
			detectFunctors(outDir)
		}

		def checksum = calcChecksum(options.profile, options.provenance, options.liveProf)
		def cacheFile = new File(cacheDir, checksum)
		if (!cacheFile.exists() || options.debug || options.forceRecompile || options.translateOnly) {

			if (options.removeContexts) {
				removeContexts(scriptFile)
			}

			File executable = new File(outDir, exeName)
			String executablePath = executable.canonicalPath
			String scriptFilePath = scriptFile.canonicalPath
			String souffleCmd = 'souffle'
			String outputCpp = "${executablePath}.cpp"
			String outputCppOpts = "-g ${outputCpp}"
			String outputOpts = options.translateOnly ? outputCppOpts : "-c -o ${executablePath}"
			// On Windows, compile logic to C++ via WSL/Souffle.
			if (OS.win) {
				log.warn("WARNING: Windows detected, using experimental WSL/Cygwin mode.")
				souffleCmd = 'wsl souffle'
				executablePath = makePathWsl(executablePath)
				scriptFilePath = makePathWsl(scriptFilePath)
				outputOpts = outputCppOpts
			}

			def compilationCommand = "${souffleCmd} ${outputOpts} ${scriptFilePath}".split().toList()
			if (factsDir)
				compilationCommand << ("-F${factsDir}" as String)
			if (options.profile)
				compilationCommand << ("-p${outDir}/profile.txt" as String)
			if (options.debug)
				compilationCommand << ("-r${outDir}/report.html" as String)
			if (options.provenance)
			// Another possible mode is 'explore' but does not support history.
				compilationCommand << ("--provenance=explain" as String)
			if (options.liveProf)
				compilationCommand << ("--live-profile" as String)
                        compilationCommand << ("-j${options.jobs}" as String)

			log.info "Compiling Datalog..."
			log.debug "Compilation command: $compilationCommand"

			def ignoreCounter = 0
			compilationTime = Helper.timing {
				Path tmpFile = Files.createTempFile("", "")
				File tmpFile0 = tmpFile.toFile()
				tmpFile0.deleteOnExit()
				executor.executeWithRedirectedOutput(compilationCommand, tmpFile0) { String line ->
					if (ignoreCounter != 0) ignoreCounter--
					else if (line.startsWith("Warning: No rules/facts defined for relation") ||
							line.startsWith("Warning: Deprecated output qualifier was used")) {
						log.info line
						ignoreCounter = 2
					} else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
					else log.info line
				}
				if (OS.win) {
					prepareSourcesForWindowsCompilation(executable, tmpFile0)
					return null
				}
				Files.delete(tmpFile)
			}
			log.info "Analysis compilation time (sec): $compilationTime"
			if (options.translateOnly) {
				log.info "Stopping at C++ translation: ${outputCpp}"
				return null
			}
			cacheCompiledBinary(executable, cacheFile, checksum)
		} else {
			logCachedExecutable(cacheFile)
		}
		return cacheFile
	}

	protected void logCachedExecutable(File cacheFile) {
		log.info "Using cached analysis executable ${cacheFile.absolutePath}"
	}

	void cacheCompiledBinary(File executable, File cacheFile, String checksum) {
		try {
			// COPY_ATTRIBUTES: Keep execute permission
			Files.copy(executable.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
			log.info "Caching analysis executable $checksum in $cacheDir"
		} catch (FileAlreadyExistsException e) {
			// If a cached file is already there, don't overwrite it
			// (it might be used by another analysis), just reuse it.
			log.info (e.message)
		}
	}

	/**
	 * Subclasses can override this method to post-process the facts before analysis.
	 */
	void postprocessFacts(File outDir, boolean profile) { }

	private static List<String> getUtilsPrefix(SouffleOptions options) {
		List<String> ret = new ArrayList<>()
		if (options.maxMemory) {
			String timeoutUtil = Resource.getResource(SouffleScript.class, log, Resource.TIMEOUT_UTIL)
			if (new File(timeoutUtil).exists()) {
				println "Using ${timeoutUtil} to limit memory usage..."
				ret.addAll(['perl', timeoutUtil, '--confess', '-m', options.maxMemory] as List<String>)
			} else if (new File(CHPST_UTIL).exists()) {
				println "Using ${CHPST_UTIL} to limit memory usage..."
				ret.addAll([CHPST_UTIL, "-d ${options.maxMemory}" as String] as List<String>)
			} else
				println "Could not find mechanism to limit analysis memory consumption."
		} else if (new File(TIME_UTIL).exists()) {
			println "Using ${TIME_UTIL} to gather performance statistics..."
			ret.add(TIME_UTIL)
		}
		return ret
	}

	def run(File analysisBinary, File factsDir, File outDir, long monitoringInterval,
			Closure monitorClosure, SouffleOptions options) {

		if (analysisBinary == null) {
			log.info "No binary found, aborting."
			return [compilationTime, executionTime]
		}

		File db = new File(outDir, 'database')
		List<String> executionCommand = getUtilsPrefix(options)
		executionCommand.addAll([analysisBinary.canonicalPath, '-j' + options.jobs,
								 '-F' + factsDir.canonicalPath,
								 '-D' + db.canonicalPath] as List<String>)
		if (options.profile)
			executionCommand << ("-p${outDir}/profile.txt" as String)

		def cmd = executionCommand.join(" ")
		if (options.provenance || options.liveProf) {
			def mode = options.provenance ? "provenance" : (options.liveProf ? "live profiling" : "unknown")
			println "This process will now exit, run this command to run the analysis with the requested interactive mode (${mode}): rlwrap ${cmd}"
			throw DoopErrorCodeException.error22()
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
                        SouffleOptions options) {

		setScriptFileViaCPP(origScriptFile, outDir)

	    def db = new File(outDir, "database")

        if (options.removeContexts)
            removeContexts(scriptFile)

		List<String> interpretationCommand = getUtilsPrefix(options)
		interpretationCommand.addAll(['souffle', scriptFile.canonicalPath,
									  '-j' + options.jobs, '-F' + factsDir.canonicalPath,
									  '-D' + db.canonicalPath] as List<String>)
        if (options.profile)
            interpretationCommand<< ("-p${outDir}/profile.txt" as String)
        if (options.debug)
            interpretationCommand << ("-r${outDir}/report.html" as String)

        log.info "Interpreting analysis script"
		def cmd = interpretationCommand.join(" ")
        log.debug "Interpretation command: ${cmd}"

        def ignoreCounter = 0
        executionTime = Helper.timing {
            Path tmpFile = Files.createTempFile("", "")
            tmpFile.toFile().deleteOnExit()
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

        log.info "Analysis execution time (sec): ${executionTime}"
        return [compilationTime, executionTime]
    }

	// Detect libfunctors.so and create corresponding symbolic link.
	private void detectFunctors(File outDir) {
		String envVar = "LD_LIBRARY_PATH"
		String ldLibPath = System.getenv(envVar)
		if(ldLibPath != null) {
			String libfunctors = null
			ldLibPath.split(File.pathSeparator).each {
				File f = new File("${it}/libfunctors.so")
				if (f.exists()) {
					libfunctors = f.canonicalPath
				}
			}
			String libName = "libfunctors.so"
			if (libfunctors != null) {
				try {
					Path target = FileSystems.default.getPath(libfunctors)
					Path link = FileSystems.default.getPath(outDir.absolutePath + File.separator + libName)
					Files.createSymbolicLink(link, target)
					log.debug "Created symbolic link: ${link} -> ${target}"
				} catch (UnsupportedOperationException ignored) {
					log.debug "Filesystem does not support symbolic link for file ${libfunctors}"
					// Fallback (non-portable).
					// executor.execute("ln -s ${libfunctors} libfunctors.so".split().toList()) { log.info it }
				} catch (FileAlreadyExistsException) {
					log.warn "WARNING: Could not create link to ${libName}, file already exists."
				}
			} else {
				log.warn "WARNING: No ${libName} in environment variable ${envVar} = '${ldLibPath}'"
			}
		}
	}

	private static void removeContexts(File scriptFile) {
		def backupFile = new File("${scriptFile}.backup")
		Files.copy(scriptFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
		ContextRemover.removeContexts(backupFile, scriptFile)
	}

	/**
	 * Replace "C:\foo\bar" with "/mnt/c/foo/bar" for WSL compatibility.
	 * @param path  the original path
	 * @return      the WSL-compatible path
	 */
	private static String makePathWsl(String path) {
		String ret = path.replace('\\', '/')
		for (char c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.toCharArray()) {
			if (ret.startsWith("${c}:")) {
				ret = '/mnt/' + Character.toLowerCase(c) + ret.substring(2)
				break
			}
		}
		println "${path} -> ${ret}"
		return ret
	}

	void prepareSourcesForWindowsCompilation(File executable, File consoleOutputFile) {
		Path tmpSouffleInclude = Files.createTempDirectory("")
		for (String includeDir : ['/usr/local/include/souffle', '/usr/include/souffle']) {
			List<String> copy = ['wsl', 'cp', '-R', includeDir, makePathWsl(tmpSouffleInclude.toString())] as List<String>
			println copy
			try {
				executor.executeWithRedirectedOutput(copy, consoleOutputFile, { println it })
			} catch (Throwable t) {
				println "ERROR: ${t.message}"
			}
		}

		// Stop evaluation and show compilation command for user.
		List<String> compile = ['g++',
								'-I', tmpSouffleInclude.toString(),
								'-Wa,-mbig-obj',
								'-O3',
								executable.canonicalPath + '.cpp',
								'-o', executable.canonicalPath] as List<String>
		println("Use this command to compile the analysis logic: " + compile.join(' ') + '\n' +
				"Then, rerun Doop with the binary (option --${DoopAnalysisFamily.USE_ANALYSIS_BINARY_NAME}).")
	}
}
