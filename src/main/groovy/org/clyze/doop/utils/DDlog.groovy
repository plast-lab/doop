package org.clyze.doop.utils

import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked
import groovy.util.logging.Log4j

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.apache.log4j.Logger
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.utils.Executor
import org.clyze.utils.Helper

/**
 * This class compiles and runs Souffle analysis with DDlog via the DDlog converter.
 */
@Log4j
@TypeChecked
class DDlog extends SouffleScript {

	static final String convertedLogicName = "converted_logic" as String
    static final String SOUFFLE_CONVERTER = "souffle_converter.py" as String

    DDlog(Executor executor) {
        super(executor)
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
			throw new DoopErrorCodeException(24, "Environment variable ${DDLOG_DIR} is empty.")
		} else {
			File f = new File(ddlogDir)
			if (!f.exists()) {
				throw new DoopErrorCodeException(26, "Directory ${DDLOG_DIR}=${ddlogDir} does not exist.")
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
    void checkOptions(boolean debug, boolean provenance, boolean liveProf,
                      boolean removeContext, boolean useFunctors) {
        if (debug) {
            throw new DoopErrorCodeException(27, "Option 'debug' is not supported.")
        } else if (provenance) {
            throw new DoopErrorCodeException(27, "Option 'provenance' is not supported.")
        } else if (liveProf) {
            throw new DoopErrorCodeException(27, "Option 'liveProf' is not supported.")
        } else if (removeContext) {
            throw new DoopErrorCodeException(27, "Option 'removeContext' is not supported.")
        } else if (useFunctors) {
            throw new DoopErrorCodeException(27, "Option 'useFunctors' is not supported.")
        }
    }

    @Override
	File compile(File origScriptFile, File outDir, File cacheDir,
                 boolean profile = false, boolean debug = false,
                 boolean provenance = false, boolean liveProf = false,
                 boolean forceRecompile = true, boolean removeContext = false, boolean useFunctors = false) {

        checkOptions(debug, provenance, liveProf, removeContext, useFunctors)

        scriptFile = File.createTempFile("gen_", ".dl", outDir)
		preprocess(scriptFile, origScriptFile)

        def checksum = calcChecksum(profile, provenance, liveProf)
		def cacheFile = new File(cacheDir, checksum)
		if (!cacheFile.exists() || forceRecompile) {
            def jobs = ((Runtime.runtime.availableProcessors() / 2) + 1) as Integer
            log.info "Compiling Datalog to Rust program and executable using ${jobs} jobs"
            def executable = compileWithDDlog(jobs, outDir)
            cacheCompiledBinary(executable, cacheFile, checksum, cacheDir)
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
			def cmdGenRust = "stack run -- -i ${convertedLogic} --action=compile -L lib".split().toList()
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

    /**
     * Read Doop home from environment variable.
     */
    private String getDoopHome() {
        String doopHome = System.getenv("DOOP_HOME")
		if (!doopHome) {
			throw new DoopErrorCodeException(24, "Environment variable DOOP_HOME is empty.")
		} else {
			log.debug "Using Doop home: ${doopHome}"
            return doopHome
		}
    }

    private String getConvertedLogicPrefix(outDir) {
		return "${outDir}/${convertedLogicName}" as String
    }

    private String getAnalysisBinary(File outDir) {
        String buildDir = getBuildDir(outDir)
        return "${buildDir}/target/release/${convertedLogicName}_cli" as String
    }

    @Override
    def run(File cacheFile, File factsDir, File outDir,
            int jobs, long monitoringInterval, Closure monitorClosure,
            boolean provenance = false, boolean liveProf = false,
            boolean profile = false) {

        checkOptions(false, provenance, liveProf, false, false)
        def db = makeDatabase(outDir)
        log.info "Running the analysis (using ${jobs} jobs)..."
        try {
            executionTime = Helper.timing {
                def dump = "${db.canonicalPath}/dump"
                def convertedLogicPrefix = getConvertedLogicPrefix(outDir)
                def dat = "${convertedLogicPrefix}.dat"
                // Hack: use script to get away with redirection.
                def analysisBinary = cacheFile.absolutePath
                def cmdRun = ((profile && new File(SouffleScript.TIME_UTIL).exists()) ? [SouffleScript.TIME_UTIL] : []) as List
                cmdRun += "${doopHome}/bin/run-with-redirection.sh ${dat} ${dump} ${analysisBinary} -w ${jobs} --no-print".split().toList()
                executeCmd(cmdRun, null)
            }
            log.info "Analysis execution time (sec): ${executionTime}"
            return [compilationTime, executionTime]
        } catch (ex) {
            throw new DoopErrorCodeException(25, ex)
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
		log.debug command
		Path tmpFile = Files.createTempFile("", "")
		if (workingDir) {
			executor.currWorkingDir = workingDir
		}
		executor.executeWithRedirectedOutput(command, tmpFile.toFile()) { println it }
		Files.delete(tmpFile)
	}

    @Override
    def interpretScript(File origScriptFile, File outDir, File factsDir,
                        int jobs, boolean profile = false, boolean debug = false,
                        boolean removeContext = false) {
        throw new DoopErrorCodeException(27, "Option 'interpret' is not supported.")
    }
}
