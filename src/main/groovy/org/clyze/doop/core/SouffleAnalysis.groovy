package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.jimple.JimpleProcessor
import org.clyze.doop.soot.DoopConventions
import org.clyze.doop.utils.ConfigurationGenerator
import org.clyze.doop.utils.DDlog
import org.clyze.doop.utils.SouffleOptions
import org.clyze.doop.utils.SouffleScript
import org.clyze.doop.utils.TACGenerator
import org.clyze.doop.utils.XTractor
import org.clyze.utils.Executor
import org.clyze.utils.JHelper

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory
import static org.apache.commons.io.FilenameUtils.getBaseName

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
		runAnalysisAndProduceStats(analysis)

		File runtimeMetricsFile = File.createTempFile('Stats_Runtime', '.csv')
		log.debug "Using intermediate runtime metrics file: ${runtimeMetricsFile.canonicalPath}"
		runtimeMetricsFile.deleteOnExit()
		runtimeMetricsFile.createNewFile()

		SouffleScript script = newScriptForAnalysis(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		String analysisBinaryPath = options.USE_ANALYSIS_BINARY.value as String
		boolean runInterpreted = options.SOUFFLE_MODE.value == DoopAnalysisFamily.SOUFFLE_INTERPRETED
		long monitorInterval = (options.X_MONITORING_INTERVAL.value as long) * 1000
		SouffleOptions souffleOpts = new SouffleOptions(options)

		if (!options.FACTS_ONLY.value && !analysisBinaryPath && !runInterpreted) {
			if (options.VIA_DDLOG.value) {
				// Copy the DDlog converter, needed both for logic
				// compilation and fact post-processing.
				DDlog.copyDDlogConverter(log, outDir)
			}
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysis, outDir, souffleOpts)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		File generatedFile
		if (options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			if (runInterpreted)
				log.info "Ignoring option --${options.X_SERIALIZE_FACTGEN_COMPILATION.name} when running in interpreted mode."
			else {
				generatedFile = compilationFuture.get()
				System.gc()
			}
		}

		try {
			log.info "[Task FACTS...]"
			generateFacts()
			script.postprocessFacts(outDir, souffleOpts.profile)
			log.info "[Task FACTS Done]"
			runtimeMetricsFile.append("fact generation time (sec)\t${factGenTime}\n")

			if (options.X_SERVER_CHA.value) {
				log.info "[CHA...]"
				def methodLookupFile = new File("${Doop.souffleLogicPath}/addons/server-logic/method-lookup-ext.dl")
                if (runInterpreted) {
                    script.interpretScript(methodLookupFile, outDir, factsDir, souffleOpts)
                } else {
				    def generatedFile0 = script.compile(methodLookupFile, outDir, souffleOpts)
				    script.run(generatedFile0, factsDir, outDir,
						       monitorInterval, monitorClosure, souffleOpts)
                }
				log.info "[CHA Done]"
			}

			if (options.FACTS_ONLY.value) return

			if (!analysisBinaryPath && !runInterpreted) {
				if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
					generatedFile = compilationFuture.get()
				}
				runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			}

			if (!options.DRY_RUN.value) {
				if (runInterpreted) {
					script.interpretScript(analysis, outDir, factsDir, souffleOpts)
				} else {
				    File analysisBinary = analysisBinaryPath ? new File(analysisBinaryPath) : generatedFile
				    script.run(analysisBinary, factsDir, outDir,
						       monitorInterval, monitorClosure, souffleOpts)
				}

				runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
				int dbSize = (sizeOfDirectory(database) / 1024).intValue()
				runtimeMetricsFile.append("disk footprint (KB)\t${dbSize}\n")
				postprocess()

				if (this.name == "xtractor") XTractor.run(this)
			}

			Files.move(runtimeMetricsFile.toPath(), new File(database, "Stats_Runtime.csv").toPath(), StandardCopyOption.REPLACE_EXISTING)
		} finally {
			executorService.shutdownNow()
		}
	}

	void initDatabase(File analysis) {
		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/facts/facts.dl")
		handleImportDynamicFacts()
	}

	void runAnalysisAndProduceStats(File analysis) {
		mainAnalysis(analysis)
		produceStats(analysis)
	}

	void mainAnalysis(File analysis) {

		// Check the open programs argument before calling the preprocessor.
		String openProgramsProfile = null
		String openProgramsRules = options.OPEN_PROGRAMS.value
		if (openProgramsRules) {
			openProgramsProfile = "${Doop.souffleLogicPath}/addons/open-programs/rules-${openProgramsRules}.dl"
			if (!(new File(openProgramsProfile)).exists())
				throw DoopErrorCodeException.error35("Open program rules profile does not exist: " + openProgramsProfile)
		}

		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/basic.dl")
		cpp.includeAtEnd("$analysis", "${Doop.souffleAnalysesPath}/${getBaseName(analysis.name)}/analysis.dl")

		if (options.INFORMATION_FLOW.value) {
			String infoflowDir = "${Doop.souffleLogicPath}/addons/information-flow"
			if (options.ANALYSIS.value == 'data-flow')
				cpp.includeAtEnd("$analysis", "${infoflowDir}/rules-data-flow.dl")
			else
				cpp.includeAtEnd("$analysis", "${infoflowDir}/rules.dl")
			cpp.includeAtEnd("$analysis", "${infoflowDir}/${options.INFORMATION_FLOW.value}${INFORMATION_FLOW_SUFFIX}.dl")
		}

		if (openProgramsProfile) {
			log.debug "Using open-programs rules: ${openProgramsRules}"
			cpp.includeAtEnd("$analysis", openProgramsProfile)
		}

		if (options.SANITY.value) {
			cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/addons/sanity.dl")
			if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
				log.warn("WARNING: The sanity check is not fully compatible with --" + options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.name)
			}
			if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
				log.warn("WARNING: The sanity check is not fully compatible with --" + options.DISTINGUISH_ALL_STRING_CONSTANTS.name)
			}
			if (options.NO_MERGES.value) {
				log.warn("WARNING: The sanity check is not fully compatible with --" + options.NO_MERGES.name)
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
					log.warn "WARNING: Ignoring file not ending in .dl: ${extraLogicPath}"
			}
		}
	}

	void produceStats(File analysis) {
		def statsPath = "${Doop.souffleLogicPath}/addons/statistics"
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
			log.error "ERROR: Configuration generation failed: ${t.message}"
		}

		try {
			if (options.SARIF.value && options.GENERATE_JIMPLE.value) {
				String version = JHelper.getVersionInfo(Doop.class)
				new JimpleProcessor(DoopConventions.jimpleDir(factsDir.canonicalPath), database, database, version, false).process()
			}
		} catch (Throwable t) {
			log.error "ERROR: SARIF generation failed: ${t.message}"
		}

		if (options.GENERATE_TAC.value) {
			TACGenerator.run(factsDir, new File(factsDir, "Methods.tac"))
		}
	}
}
