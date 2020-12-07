package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.utils.ConfigurationGenerator
import org.clyze.doop.utils.DDlog
import org.clyze.doop.utils.SouffleScript
import org.clyze.utils.Executor

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

import static org.apache.commons.io.FilenameUtils.getBaseName
import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.moveFile
import static org.apache.commons.io.FileUtils.sizeOfDirectory

@CompileStatic
@InheritConstructors
@Log4j
class SouffleAnalysis extends DoopAnalysis {

	@Override
	void run() {
		File analysis = new File(outDir, "${name}.dl")
		deleteQuietly(analysis)
		analysis.createNewFile()

		initDatabase(analysis)
		basicAnalysis(analysis)
		if (!options.X_STOP_AT_BASIC.value) {
			runAnalysisAndProduceStats(analysis)
		}

		def script = newScriptForAnalysis(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		boolean provenance = options.SOUFFLE_PROVENANCE.value as boolean
		boolean profiling = options.SOUFFLE_PROFILE.value as boolean
		boolean liveProf = options.SOUFFLE_LIVE_PROFILE.value as boolean
		String analysisBinaryPath = options.USE_ANALYSIS_BINARY.value
		if (!options.X_STOP_AT_FACTS.value && !analysisBinaryPath) {
			if (options.VIA_DDLOG.value) {
				// Copy the DDlog converter, needed both for logic
				// compilation and fact post-processing.
				DDlog.copyDDlogConverter(log, outDir)
			}
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysis, outDir,
							profiling,
							options.SOUFFLE_DEBUG.value as boolean,
							provenance,
							liveProf,
							options.SOUFFLE_FORCE_RECOMPILE.value as boolean,
							options.X_CONTEXT_REMOVER.value as boolean,
							options.SOUFFLE_USE_FUNCTORS.value as boolean)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		File runtimeMetricsFile = File.createTempFile('Stats_Runtime', '.csv')
		log.debug "Using intermediate runtime metrics file: ${runtimeMetricsFile.canonicalPath}"
		if (!database.exists()) {
			database.mkdirs()
		}
		runtimeMetricsFile.createNewFile()

		File generatedFile
		if (options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			generatedFile = compilationFuture.get()
			System.gc()
		}

		try {
			log.info "[Task FACTS...]"
			generateFacts()
			script.postprocessFacts(outDir, profiling)
			log.info "[Task FACTS Done]"
			runtimeMetricsFile.append("soot-fact-generation time (sec)\t${factGenTime}\n")

			if (options.X_SERVER_CHA.value) {
				log.info "[CHA...]"
				def methodLookupFile = new File("${Doop.souffleAddonsPath}/server-logic/method-lookup-ext.dl")
				def generatedFile0 = script.compile(methodLookupFile, outDir,
						profiling,
						options.SOUFFLE_DEBUG.value as boolean,
						provenance,
						liveProf,
						options.SOUFFLE_FORCE_RECOMPILE.value as boolean,
						options.X_CONTEXT_REMOVER.value as boolean)
				script.run(generatedFile0, factsDir, outDir, options.SOUFFLE_JOBS.value as int,
						   (options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure,
						   provenance, liveProf, profiling)
				log.info "[CHA Done]"
			}

			if (options.X_STOP_AT_FACTS.value) return

			if (!analysisBinaryPath) {
				if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
					generatedFile = compilationFuture.get()
				}
				runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			}

			if (!options.X_DRY_RUN.value) {
				File analysisBinary = analysisBinaryPath ? new File(analysisBinaryPath) : generatedFile
				script.run(analysisBinary, factsDir, outDir, options.SOUFFLE_JOBS.value as int,
						(options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure,
						provenance, liveProf, profiling)

				runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
				int dbSize = (sizeOfDirectory(database) / 1024).intValue()
				runtimeMetricsFile.append("disk footprint (KB)\t${dbSize}\n")
				postprocess()

			}

			moveFile(runtimeMetricsFile, new File(database, "Stats_Runtime.csv"))
		} finally {
			executorService.shutdownNow()
		}
	}

	void initDatabase(File analysis) {
		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/facts/facts.dl")
		handleImportDynamicFacts()
	}

	void basicAnalysis(File analysis) {
		if (options.X_STOP_AT_BASIC.value == 'classes-scc') {
			cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/classes-scc.dl")
		}
		if (options.X_STOP_AT_BASIC.value == 'partitioning') {
			cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/partitioning.dl")
		}
		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/basic.dl")
	}

	void runAnalysisAndProduceStats(File analysis) {
		mainAnalysis(analysis)
		produceStats(analysis)
	}

	void mainAnalysis(File analysis) {
		cpp.includeAtEnd("$analysis", "${Doop.souffleAnalysesPath}/${getBaseName(analysis.name)}/analysis.dl")

		if (options.INFORMATION_FLOW.value)
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/information-flow/${options.INFORMATION_FLOW.value}${INFORMATION_FLOW_SUFFIX}.dl")

		if (options.CONSTANT_FOLDING.value)
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/constant-folding/analysis.dl")

		if (options.SYMBOLIC_REASONING.value)
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/symbolic-reasoning/analysis.dl")

		String openProgramsRules = options.OPEN_PROGRAMS.value
		if (openProgramsRules) {
			log.debug "Using open-programs rules: ${openProgramsRules}"
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/open-programs/rules-${openProgramsRules}.dl")
		}

		if (options.SANITY.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/sanity.dl")
			if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
				log.warn("WARNING: the sanity check is not fully compatible with --" + options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.name)
			}
			if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
				log.warn("WARNING: the sanity check is not fully compatible with --" + options.DISTINGUISH_ALL_STRING_CONSTANTS.name)
			}
			if (options.NO_MERGES.value) {
				log.warn("WARNING: the sanity check is not fully compatible with --" + options.NO_MERGES.name)
			}
		}

		if (!options.X_STOP_AT_FACTS.value && options.X_SERVER_LOGIC.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/server-logic/queries.dl")
		}

		if (!options.X_STOP_AT_FACTS.value && options.GENERATE_OPTIMIZATION_DIRECTIVES.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/opt-directives/keep.dl")
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/opt-directives/directives.dl")
		}

		if (options.X_ORACULAR_HEURISTICS.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/oracular/oracular-heuristics.dl")
		}

		if (options.X_CONTEXT_DEPENDENCY_HEURISTIC.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/oracular/2-object-ctx-dependency-heuristic.dl")
		}

		if (options.X_EXTRA_LOGIC.value) {
			File extraLogic = new File(options.X_EXTRA_LOGIC.value as String)
			if (extraLogic.exists()) {
				String extraLogicPath = extraLogic.canonicalPath
				log.info "Adding extra logic file ${extraLogicPath}"
				cpp.includeAtEnd("${analysis}", extraLogicPath)
			} else {
				throw new RuntimeException("Extra logic file does not exist: ${extraLogic}")
			}
		}
	}

	void produceStats(File analysis) {
		def statsPath = "${Doop.souffleAddonsPath}/statistics"
		if (options.X_EXTRA_METRICS.value) {
			cpp.includeAtEnd("$analysis", "${statsPath}/metrics.dl")
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

	@Override
	void processRelation(String query, Closure outputLineProcessor) {
		query = query.replaceAll(":", "_")
		def file = new File(this.outDir, "database/${query}.csv")
		if (!file.exists()) throw new FileNotFoundException(file.canonicalPath)
		file.eachLine { outputLineProcessor.call(it) }
	}

	protected SouffleScript newScriptForAnalysis(Executor executor) {
		boolean viaDDlog = options.VIA_DDLOG.value as Boolean
		File cacheDir = new File(Doop.souffleAnalysesCache, name)
		return SouffleScript.newScript(executor, cacheDir, viaDDlog)
	}

	protected void postprocess() {
		try {
			if (options.GENERATE_OPTIMIZATION_DIRECTIVES.value) {
				File configurationsDir = new File(database, 'configurations')
				configurationsDir.mkdirs()
				new ConfigurationGenerator(outDir.canonicalPath, configurationsDir.canonicalPath).generateConfigurations()
			}
		} catch (Throwable t) {
			log.error "ERROR: configuration generation failed."
		}
	}
}
