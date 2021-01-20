package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.jimple.JimpleProcessor
import org.clyze.doop.soot.DoopConventions
import org.clyze.doop.utils.ConfigurationGenerator
import org.clyze.doop.utils.DDlog
import org.clyze.doop.utils.SouffleScript
import org.clyze.utils.Executor
import org.clyze.utils.JHelper

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

		File runtimeMetricsFile = File.createTempFile('Stats_Runtime', '.csv')
		runtimeMetricsFile.deleteOnExit()
		log.debug "Using intermediate runtime metrics file: ${runtimeMetricsFile.canonicalPath}"
		if (!database.exists()) {
			database.mkdirs()
		}
		runtimeMetricsFile.createNewFile()

		SouffleScript script = newScriptForAnalysis(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		boolean provenance = options.SOUFFLE_PROVENANCE.value as boolean
		boolean profiling = options.SOUFFLE_PROFILE.value as boolean
		boolean liveProf = options.SOUFFLE_LIVE_PROFILE.value as boolean
		String analysisBinaryPath = options.USE_ANALYSIS_BINARY.value as String
		boolean runInterpreted = options.SOUFFLE_RUN_INTERPRETED.value as boolean
		boolean debug = options.SOUFFLE_DEBUG.value as boolean
		int jobs = options.SOUFFLE_JOBS.value as int
		boolean removeContexts = options.X_CONTEXT_REMOVER.value as boolean
		boolean forceRecompile = options.SOUFFLE_FORCE_RECOMPILE.value as boolean
		long monitorInterval = (options.X_MONITORING_INTERVAL.value as long) * 1000

		if (!options.X_STOP_AT_FACTS.value && !analysisBinaryPath && !runInterpreted) {
			if (options.VIA_DDLOG.value) {
				// Copy the DDlog converter, needed both for logic
				// compilation and fact post-processing.
				DDlog.copyDDlogConverter(log, outDir)
			}
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysis, outDir, profiling,
													   debug, provenance, liveProf, forceRecompile,
													   removeContexts,
													   options.SOUFFLE_USE_FUNCTORS.value as boolean)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

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
                if (runInterpreted) {
                    script.interpretScript(methodLookupFile, outDir, factsDir,
                                           jobs, profiling, debug, removeContexts)
                } else {
				    def generatedFile0 = script.compile(methodLookupFile, outDir,
						                                profiling, debug, provenance, liveProf, forceRecompile,
						                                removeContexts)
				    script.run(generatedFile0, factsDir, outDir, jobs,
						       monitorInterval, monitorClosure,
						       provenance, liveProf, profiling)
                }
				log.info "[CHA Done]"
			}

			if (options.X_STOP_AT_FACTS.value) return

			if (!analysisBinaryPath && !runInterpreted) {
				if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
					generatedFile = compilationFuture.get()
				}
				runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			}

			if (!options.DRY_RUN.value) {
				if (runInterpreted) {
					script.interpretScript(analysis, outDir, factsDir, jobs,
										   profiling, debug, removeContexts)
				} else {
				    File analysisBinary = analysisBinaryPath ? new File(analysisBinaryPath) : generatedFile
				    script.run(analysisBinary, factsDir, outDir, jobs, 
						       monitorInterval, monitorClosure, provenance,
                               liveProf, profiling)
				}

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

		if (options.EXTRA_LOGIC.value) {
			Collection<String> extras = options.EXTRA_LOGIC.value as List<String>
			for (String extraFile : extras) {
				File extraLogic = new File(extraFile)
				if (!extraLogic.exists())
					throw new RuntimeException("Extra logic file does not exist: ${extraLogic}")
				String extraLogicPath = extraLogic.canonicalPath
				// Safety: check file extension to avoid using this mechanism
				// to read files from anywhere in the system.
				if (extraLogicPath.endsWith('.dl')) {
					log.info "Adding extra logic file ${extraLogicPath}"
					cpp.includeAtEnd("${analysis}", extraLogicPath)
				} else
					log.warn "WARNING: ignoring file not ending in .dl: ${extraLogicPath}"
			}
		}
	}

	void produceStats(File analysis) {
		def statsPath = "${Doop.souffleAddonsPath}/statistics"
		if (options.X_EXTRA_METRICS.value) {
			cpp.includeAtEnd("$analysis", "${statsPath}/metrics.dl")
		}

		if (options.X_STATS_NONE.value) return

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

		try {
			if (options.SARIF.value && options.GENERATE_JIMPLE.value) {
				String version = JHelper.getVersionInfo(Doop.class)
				new JimpleProcessor(DoopConventions.jimpleDir(factsDir.canonicalPath), database, database, version, false).process()
			}
		} catch (Throwable t) {
			log.error "ERROR: SARIF generation failed: ${t.message}"
		}
	}
}
