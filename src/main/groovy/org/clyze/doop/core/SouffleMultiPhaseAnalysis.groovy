package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.transform.TypeChecked
import groovy.util.logging.Log4j
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.ptatoolkit.scaler.Driver
import org.clyze.doop.utils.SouffleScript
import org.clyze.utils.CPreprocessor
import org.clyze.utils.Executor

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory
import static org.apache.commons.io.FilenameUtils.getBaseName

//@CompileStatic
@InheritConstructors
@Log4j
//@TypeChecked
class SouffleMultiPhaseAnalysis extends DoopAnalysis {

	File preAnalysis
	File analysis

	@Override
	void run() {
		preAnalysis = new File(outDir, "context-insensitive.dl")
		analysis = new File(outDir, "${name}.dl")
		deleteQuietly(preAnalysis)
		deleteQuietly(analysis)
		preAnalysis.createNewFile()
		analysis.createNewFile()

		options.CONFIGURATION.value = "ContextInsensitiveConfiguration"
		def commandsEnv = initExternalCommandsEnvironment(options)

		executor = new Executor(outDir, commandsEnv)
		cpp = new CPreprocessor(this, executor)

		initDatabase(preAnalysis)
		basicAnalysis(preAnalysis)
		if (!options.X_STOP_AT_BASIC.value) {
			mainAnalysis(preAnalysis)
			produceStats(preAnalysis)
		}

		def cacheDir = new File(Doop.souffleAnalysesCache, name)
		cacheDir.mkdirs()
		def script = new SouffleScript(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		boolean provenance = options.SOUFFLE_PROVENANCE.value as boolean
		boolean liveProf = options.SOUFFLE_LIVE_PROFILE.value as boolean
		if (!options.X_STOP_AT_FACTS.value) {
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(preAnalysis, outDir, cacheDir,
							options.SOUFFLE_PROFILE.value as boolean,
							options.SOUFFLE_DEBUG.value as boolean,
							provenance,
							liveProf,
							options.SOUFFLE_FORCE_RECOMPILE.value as boolean,
							options.X_CONTEXT_REMOVER.value as boolean)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		File runtimeMetricsFile = new File(database, "Stats_Runtime.csv")

		try {
			log.info "[Task FACTS...]"
			generateFacts()
			log.info "[Task FACTS Done]"

			if (options.X_STOP_AT_FACTS.value) return

			def generatedFile = compilationFuture.get()
			script.run(generatedFile, factsDir, outDir, options.SOUFFLE_JOBS.value as int,
					(options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure)

			int dbSize = (sizeOfDirectory(database) / 1024).intValue()
			runtimeMetricsFile.createNewFile()
			runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
			runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
			runtimeMetricsFile.append("soot-fact-generation time (sec)\t$factGenTime\n")
		} finally {
			executorService.shutdownNow()
		}
		printStats()
		//Scaler Execution
		Driver scalerMain = new Driver()
		scalerMain.runScaler(factsDir, database)
		//scalerMain.runScaler(factsDir, database)
		options.X_START_AFTER_FACTS.value = factsDir
		options.CONFIGURATION.value = "FullyGuidedContextSensitiveConfiguration"
		options.SCALER_PRE_ANALYSIS.value = null
		executor = new Executor(outDir, commandsEnv)
		cpp = new CPreprocessor(this, executor)

		initDatabase(analysis)
		basicAnalysis(analysis)
		if (!options.X_STOP_AT_BASIC.value) {
			mainAnalysis(analysis)
			produceStats(analysis)
		}

		compilationFuture = null
		executorService = Executors.newSingleThreadExecutor()
		if (!options.X_STOP_AT_FACTS.value) {
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysis, outDir, cacheDir,
							options.SOUFFLE_PROFILE.value as boolean,
							options.SOUFFLE_DEBUG.value as boolean,
							provenance,
							liveProf,
							options.SOUFFLE_FORCE_RECOMPILE.value as boolean,
							options.X_CONTEXT_REMOVER.value as boolean)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		runtimeMetricsFile = new File(database, "Stats_Runtime.csv")

		try {
			if (options.X_STOP_AT_FACTS.value) return

			def generatedFile = compilationFuture.get()
			script.run(generatedFile, factsDir, outDir, options.SOUFFLE_JOBS.value as int,
					(options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure, provenance, liveProf)

			int dbSize = (sizeOfDirectory(database) / 1024).intValue()
			runtimeMetricsFile.createNewFile()
			runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
			runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
			runtimeMetricsFile.append("soot-fact-generation time (sec)\t$factGenTime\n")
		} finally {
			executorService.shutdownNow()
		}
	}

	void initDatabase(File analysis) {
		def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/flow-sensitive-schema.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/flow-insensitive-schema.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/import-entities.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/import-facts.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/to-flow-sensitive.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/post-process.dl", commonMacros)
		cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/mock-heap.dl", commonMacros)

		handleImportDynamicFacts()

		if (options.HEAPDLS.value || options.IMPORT_DYNAMIC_FACTS.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/import-dynamic-facts.dl", commonMacros)
		}

		if (options.TAMIFLEX.value) {
			def tamiflexPath = "${Doop.souffleAddonsPath}/tamiflex"
			cpp.includeAtEnd("$analysis", "${tamiflexPath}/fact-declarations.dl")
			cpp.includeAtEnd("$analysis", "${tamiflexPath}/import.dl")
		}
	}

	void basicAnalysis(File analysis) {
		def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/basic.dl", commonMacros)

		if (options.CFG_ANALYSIS.value || name == "sound-may-point-to") {
			def cfgAnalysisPath = "${Doop.souffleAddonsPath}/cfg-analysis"
			cpp.includeAtEnd("$analysis", "${cfgAnalysisPath}/analysis.dl", "${cfgAnalysisPath}/declarations.dl")
		}
		if (options.X_STOP_AT_BASIC.value) {
			if (options.X_STOP_AT_BASIC.value == 'classes-scc') {
				cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/classes-scc.dl")
			}

			if (options.X_STOP_AT_BASIC.value == 'partitioning') {
				cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/partitioning.dl")
			}
		}
	}

	void mainAnalysis(File analysis) {
		def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
		def mainPath = "${Doop.souffleLogicPath}/main"
		log.debug("analysis: ${getBaseName(analysis.name)}")
		def analysisPath = "${Doop.souffleAnalysesPath}/${getBaseName(analysis.name)}"

		if (name == "sound-may-point-to") {
			cpp.includeAtEnd("$analysis", "${mainPath}/string-constants.dl")
			cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl")
		} else {
			cpp.includeAtEndIfExists("$analysis", "${analysisPath}/declarations.dl")
			cpp.includeAtEndIfExists("$analysis", "${analysisPath}/delta.dl", commonMacros)
			cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl", commonMacros)
		}

		if (options.INFORMATION_FLOW.value) {
			def infoFlowPath = "${Doop.souffleAddonsPath}/information-flow"
			cpp.includeAtEnd("$analysis", "${infoFlowPath}/declarations.dl")
			cpp.includeAtEnd("$analysis", "${infoFlowPath}/delta.dl")
			cpp.includeAtEnd("$analysis", "${infoFlowPath}/rules.dl")
			cpp.includeAtEnd("$analysis", "${infoFlowPath}/${options.INFORMATION_FLOW.value}${INFORMATION_FLOW_SUFFIX}.dl")
		}

		if (options.SYMBOLIC_REASONING.value) {
			def symbolicReasoningPath ="${Doop.souffleAddonsPath}/symbolic-reasoning"
			cpp.includeAtEnd("$analysis", "${symbolicReasoningPath}/const-type-infer.dl")
			cpp.includeAtEnd("$analysis", "${symbolicReasoningPath}/constant-folding.dl")
			//cpp.includeAtEnd("$analysis", "${symbolicReasoningPath}/constant-propagation.dl")
		}

		String openProgramsRules = options.OPEN_PROGRAMS.value
		if (openProgramsRules) {
			log.debug "Using open-programs rules: ${openProgramsRules}"
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/open-programs/rules-${openProgramsRules}.dl", commonMacros)
		}

		if (options.SANITY.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/sanity.dl")
			if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
				log.info("Warning: the sanity check is not fully compatible with --" + options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.name)
			}
			if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
				log.info("Warning: the sanity check is not fully compatible with --" + options.DISTINGUISH_ALL_STRING_CONSTANTS.name)
			}
			if (options.NO_MERGES.value) {
				log.info("Warning: the sanity check is not fully compatible with --" + options.NO_MERGES.name)
			}
		}

		if (!options.X_STOP_AT_FACTS.value && options.X_SERVER_LOGIC.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/server-logic/queries.dl")
		}

		if (!options.X_STOP_AT_FACTS.value && options.GENERATE_OPTIMIZATION_DIRECTIVES.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/opt-directives/keep.dl")
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/opt-directives/directives.dl")
		}

		if (options.X_EXTRA_LOGIC.value) {
			File extraLogic = new File(options.X_EXTRA_LOGIC.value as String)
			if (extraLogic.exists()) {
				String extraLogicPath = extraLogic.canonicalPath
				log.info "Adding extra logic file ${extraLogicPath}"
				cpp.includeAtEnd("${analysis}", extraLogicPath, commonMacros)
			} else {
				log.warn "Extra logic file does not exist: ${extraLogic}"
			}
		}
	}

	void produceStats(File analysis) {
		def statsPath = "${Doop.souffleAddonsPath}/statistics"
		if (options.X_EXTRA_METRICS.value) {
			cpp.includeAtEnd("$analysis", "${statsPath}/metrics.dl")
		}

		if (options.X_ORACULAR_HEURISTICS.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/oracular/oracular-heuristics.dl")
		}

		if (options.X_STATS_NONE.value) return

		if (options.X_STATS_AROUND.value) {
			cpp.includeAtEnd("$analysis", options.X_STATS_AROUND.value as String)
			return
		}

		// Special case of X_STATS_AROUND (detected automatically)
		def specialStats = new File("${Doop.souffleAnalysesPath}/${name}/statistics.dl")
		if (specialStats.exists()) {
			cpp.includeAtEnd("$analysis", specialStats.toString())
			return
		}

		cpp.includeAtEnd("$analysis", "${statsPath}/statistics-simple.dl")

		if (options.X_STATS_FULL.value || options.X_STATS_DEFAULT.value) {
			cpp.includeAtEnd("$analysis", "${statsPath}/statistics.dl")
		}
	}

	/**
	 * Initializes the external commands environment of the given analysis, by:
	 * <ul>
	 *     <li>adding the LD_LIBRARY_PATH option to the current environment
	 *     <li>modifying PATH to also include the LD_LIBRARY_PATH option
	 *     <li>adding the LOGICBLOX_HOME option to the current environment
	 *     <li>adding the DOOP_HOME to the current environment
	 *     <li>adding the LB_PAGER_FORCE_START and the LB_MEM_NOWARN to the current environment
	 *     <li>adding the variables/paths/tweaks to meet the lb-env-bin.sh requirements of the pa-datalog distro
	 * </ul>
	 */
	protected static Map<String, String> initExternalCommandsEnvironment(Map<String, AnalysisOption> options) {
		log.debug "Initializing the environment of the external commands"

		Map<String, String> env = [:]
		env.putAll(System.getenv())

		env.LC_ALL = "en_US.UTF-8"

		if (options.LB3.value) {
			def lbHome = options.LOGICBLOX_HOME.value
			env.LOGICBLOX_HOME = lbHome
			//We add these LB specific env vars here to make the server deployment more flexible (and the cli user's life easier)
			env.LB_PAGER_FORCE_START = "true"
			env.LB_MEM_NOWARN = "true"
			env.DOOP_HOME = Doop.doopHome

			//We add the following for pa-datalog to function properly (copied from the lib-env-bin.sh script)
			def path = env.PATH
			env.PATH = "${lbHome}/bin:${path ?: ""}" as String

			def ldLibraryPath = options.LD_LIBRARY_PATH.value
			env.LD_LIBRARY_PATH = "${lbHome}/lib/cpp:${ldLibraryPath ?: ""}" as String
		}

		return env
	}

	@Override
	void processRelation(String query, Closure outputLineProcessor) {
		query = query.replaceAll(":", "_")
		def file = new File(this.outDir, "database/${query}.csv")
		if (!file.exists()) throw new FileNotFoundException(file.canonicalPath)
		file.eachLine { outputLineProcessor.call(it.replaceAll("\t", ", ")) }
	}

	void printStats() {
		def lines = []


		def file = new File("${this.outDir}/database/Stats_Runtime.csv")
		file.eachLine { String line -> lines << line.replace("\t", ", ") }

		log.info "-- Runtime metrics --"
		lines.sort()*.split(", ").each {
			printf("%-80s %,d\n", it[0], it[1] as long)
		}

			lines = []

			file = new File("${this.outDir}/database/Stats_Metrics.csv")
			file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }

			log.info "-- Statistics --"
			lines.sort()*.split(", ").each {
				printf("%-80s %,d\n", it[1], it[2] as long)
			}
	}
}