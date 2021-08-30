package org.clyze.doop.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.apache.log4j.Logger
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.Doop
import org.clyze.utils.Executor
import org.clyze.utils.Helper

/**
 * This class compiles and runs Souffle analysis with DDlog via the DDlog converter.
 */
@Log4j
@CompileStatic
class DDlog extends SouffleScript {

	static final String convertedLogicName = "converted_logic" as String
    static final String SOUFFLE_CONVERTER = "souffle_converter.py" as String

    DDlog(Executor executor, File cacheDir) {
        super(executor, cacheDir)
    }

    /**
     * Reads the DDlog directory from its corresponding environment variable.
     *
     * @param log    the logger object to use for debug messages
     * @return       the DDlog path
     */
    private static File getDDlogDir(Logger log) {
		String DDLOG_DIR = "DDLOG_DIR"
		String ddlogDir = System.getenv(DDLOG_DIR)
		if (!ddlogDir) {
			throw DoopErrorCodeException.error24("Environment variable ${DDLOG_DIR} is empty.")
		} else {
			File f = new File(ddlogDir)
			if (!f.exists()) {
				throw DoopErrorCodeException.error26("Directory ${DDLOG_DIR}=${ddlogDir} does not exist.")
			} else {
				log.debug "Using DDlog in ${ddlogDir}"
				return f
			}
		}
    }

    /**
     * Copies the DDlog Souffle converter tools.
     *
     * @param log     the logger object to use for debug messages
     * @param outDir  the output directory where the files will be copied
     */
    static void copyDDlogConverter(Logger log, File outDir) {
		String ddlogSouffleDir = "${getDDlogDir(log).absolutePath}/tools"
		["souffle-grammar.pg", SOUFFLE_CONVERTER].each {
			File from = new File(ddlogSouffleDir, it)
			File to = new File(outDir, it)
			log.debug "COPY: ${from.canonicalPath} -> ${to.canonicalPath}"
			Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
		}
    }

    /*
     * Check that unsupported options are not enabled.
     */
    void checkOptions(org.clyze.doop.utils.SouffleOptions options) {
        if (options.debug) {
            throw DoopErrorCodeException.error27("Option 'debug' is not supported.")
        } else if (options.provenance) {
            throw DoopErrorCodeException.error27("Option 'provenance' is not supported.")
        } else if (options.liveProf) {
            throw DoopErrorCodeException.error27("Option 'liveProf' is not supported.")
        } else if (options.removeContexts) {
            throw DoopErrorCodeException.error27("Option 'removeContext' is not supported.")
        } else if (options.useFunctors) {
            throw DoopErrorCodeException.error27("Option 'useFunctors' is not supported.")
        }
    }

    @Override
	File compile(File origScriptFile, File outDir, SouffleOptions options) {

        checkOptions(options)

        setScriptFileViaCPP(origScriptFile, outDir)

        def checksum = calcChecksum(options.profile, options.provenance, options.liveProf)
		def cacheFile = new File(cacheDir, checksum)
		if (!cacheFile.exists() || options.forceRecompile) {
            def jobs = ((Runtime.runtime.availableProcessors() / 2) + 1) as Integer
            log.info "Compiling Datalog to Rust program and executable using ${jobs} jobs"
            def executable = compileWithDDlog(jobs, outDir)
            cacheCompiledBinary(executable, cacheFile, checksum)
		} else {
			logCachedExecutable(cacheFile)
		}
		return cacheFile

    }

    /**
     * Compile the (converted) analysis logic.
     *
     * @param jobs       the number of jobs to use when compiling the analysis
     * @param outDir     the analysis output directory
     */
    File compileWithDDlog(int jobs, File outDir) {
        // Step 1. Call converter for logic only.
        def convertedLogicPrefix = getConvertedLogicPrefix(outDir)
		def cmdConvert = ["${outDir}/${SOUFFLE_CONVERTER}" as String,
						  "--logic-only", "--convert-dnf",
						  scriptFile.canonicalPath,
						  convertedLogicPrefix ]
		log.debug "Running logic conversion command: ${cmdConvert}"
		executeCmd(cmdConvert, outDir)

		// Step 2: Compile the analysis.
		def genTime = Helper.timing {
			log.info "Compiling the analysis: code generation..."
			String convertedLogic = "${convertedLogicPrefix}.dl" as String
			def cmdGenRust = "ddlog -i ${convertedLogic} --action=compile -L lib".split().toList()
			executeCmd(cmdGenRust, getDDlogDir(log))
		}
		log.info "Code generation time: ${genTime}"
		def buildTime = Helper.timing {
			log.info "Compiling the analysis: building (using ${jobs} jobs)..."
			String buildDir = getBuildDir(outDir)
			log.debug "Build dir: ${buildDir}"
			def cmdBuildRust = "cargo build -j ${jobs} --release".split().toList()
			executeCmd(cmdBuildRust, new File(buildDir))
		}
		log.info "Build time: ${buildTime}"
		compilationTime = genTime + buildTime
		log.info "Analysis compilation time (sec): ${compilationTime}"

        return new File(getAnalysisBinary(outDir))
    }

    private String getBuildDir(File outDir) {
        def convertedLogicPrefix = getConvertedLogicPrefix(outDir)
        return "${convertedLogicPrefix}_ddlog" as String
	}

    private String getConvertedLogicPrefix(outDir) {
		return "${outDir}/${convertedLogicName}" as String
    }

    private String getAnalysisBinary(File outDir) {
        String buildDir = getBuildDir(outDir)
        return "${buildDir}/target/release/${convertedLogicName}_cli" as String
    }

    @Override
    def run(File cacheFile, File factsDir, File outDir, long monitoringInterval,
            Closure monitorClosure, SouffleOptions options) {

        checkOptions(options)
	    def db = new File(outDir, "database")
        log.info "Running the analysis (using ${options.jobs} jobs)..."
        try {
            executionTime = Helper.timing {
                def dump = "${db.canonicalPath}/dump"
                def convertedLogicPrefix = getConvertedLogicPrefix(outDir)
                def dat = "${convertedLogicPrefix}.dat"
                // Hack: use script to get away with redirection.
                def analysisBinary = cacheFile.absolutePath
                def cmdRun = ((options.profile && new File(SouffleScript.TIME_UTIL).exists()) ? [SouffleScript.TIME_UTIL] : []) as List
                cmdRun += "${Doop.doopHome}/bin/run-with-redirection.sh ${dat} ${dump} ${analysisBinary} -w ${options.jobs} --no-print".split().toList()
                executeCmd(cmdRun, null)
            }
            log.info "Analysis execution time (sec): ${executionTime}"
            return [compilationTime, executionTime]
        } catch (ex) {
            throw DoopErrorCodeException.error25(ex)
        }
    }

    /**
     * Convert the facts to DDlog commands.
     *
     * @param outDir     the output directory
     * @param profile    profiling mode
     */
    @Override
    void postprocessFacts(File outDir, boolean profile) {
        def convertedLogicPrefix = getConvertedLogicPrefix(outDir)
        def cmdConvert = ["${outDir}/${SOUFFLE_CONVERTER}" as String,
                          scriptFile.canonicalPath,
                          "--facts-only",
                          convertedLogicPrefix ]
        if (profile) {
            cmdConvert << "--profile"
        }
        log.debug "Running facts conversion command: ${cmdConvert}"
        executeCmd(cmdConvert, outDir)
    }

	/**
	 * Executes a command using a temporary file for its output.
	 *
	 * @param cmd		  the command line to invoke
	 * @param workingDir  the working directory to use
	 */
	void executeCmd(List<String> command, File workingDir) {
		String cmd = String.join(" ", command)
		log.debug "[Working directory: ${workingDir}] ${cmd}"
		Path tmpFile = Files.createTempFile("", "")
		tmpFile.toFile().deleteOnExit()
		if (workingDir) {
			executor.currWorkingDir = workingDir
		}
		executor.executeWithRedirectedOutput(command, tmpFile.toFile()) { println it }
		Files.delete(tmpFile)
	}

    @Override
    def interpretScript(File origScriptFile, File outDir, File factsDir, SouffleOptions options) {
        throw DoopErrorCodeException.error27("Option 'interpret' is not supported.")
    }
}
