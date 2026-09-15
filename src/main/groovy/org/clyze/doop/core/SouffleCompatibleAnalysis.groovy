package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.jimple.JimpleProcessor
import org.clyze.doop.soot.DoopConventions
import org.clyze.doop.utils.CPreprocessor
import org.clyze.doop.utils.ConfigurationGenerator
import org.clyze.doop.utils.TACGenerator
import org.clyze.utils.JHelper

import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FilenameUtils.getBaseName

@CompileStatic
@InheritConstructors
@Log4j
abstract class SouffleCompatibleAnalysis extends DoopAnalysis {

	// Analyses that do NOT instantiate the `mainAnalysis` Souffle component: they
	// include neither main/single-phase-analysis.dl nor main/two-phase-analysis.dl,
	// providing their own self-contained points-to instead. The central mocking
	// layer #includes mocking-core.dl, which references mainAnalysis.*, so it must
	// NOT be appended for these -- doing so fails Souffle compilation with an
	// unknown-relation error on mainAnalysis.* (e.g. the 'micro' analysis).
	static final Set<String> ANALYSES_WITHOUT_MAIN =
		['basic-only', 'data-flow', 'dependency-analysis', 'micro',
		 'sound-may-point-to', 'xtractor'] as Set

	@Override
	void run() {
		log.info "[Task ASSEMBLE...]"
		File analysis = prepareAnalysisFile()
		initDatabase(analysis)
		File generatedAnalysisFile = assembleAnalysis(analysis)
		File runtimeMetricsFile = prepareRuntimeMetricsFile()
		log.info "[Task ASSEMBLE Done]"

		log.info "[Task FACTS...]"
		generateFacts()
		log.info "[Task FACTS Done]"
		runtimeMetricsFile.append("fact generation time (sec)\t${factGenTime}\n")

		doRun(generatedAnalysisFile, runtimeMetricsFile)
		if (shouldPostProcess()) {
			postprocess()
		}
		Files.move(runtimeMetricsFile.toPath(), new File(database, "Stats_Runtime.csv").toPath(), StandardCopyOption.REPLACE_EXISTING)
	}

	abstract protected void doRun(File analysisFile, File runtimeMetricsFile)

	protected boolean shouldPostProcess() {
		return (!options.FACTS_ONLY.value && !options.DRY_RUN.value)
	}

	protected File prepareAnalysisFile() {
		File analysis = new File(outDir, "${name}.dl")
		deleteQuietly(analysis)
		analysis.createNewFile()
		return analysis
	}

	protected File prepareRuntimeMetricsFile() {
		File runtimeMetricsFile = File.createTempFile('Stats_Runtime', '.csv')
		log.debug "Using intermediate runtime metrics file: ${runtimeMetricsFile.canonicalPath}"
		runtimeMetricsFile.deleteOnExit()
		runtimeMetricsFile.createNewFile()
		return runtimeMetricsFile
	}

	protected void initDatabase(File analysis) {
		cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/facts/facts.dl")
		handleImportDynamicFacts()
	}

	protected File assembleAnalysis(File analysis) {
		assembleMainAnalysis(analysis)
		assembleAnalysisStats(analysis)

		File generatedFile = File.createTempFile("gen_", ".dl", outDir)
		cpp.disableLineMarkers().enableLogOutput()
		cpp.preprocessIfExists(generatedFile.canonicalPath, analysis.canonicalPath)
		return generatedFile
	}

	protected void assembleMainAnalysis(File analysis) {

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

		// Central open-world mock-object layer, included for every analysis that
		// instantiates the mainAnalysis component (i.e. NOT the self-contained ones in
		// ANALYSES_WITHOUT_MAIN -- the layer references mainAnalysis.* and would fail to
		// compile for them): it mocks receivers/arguments/fields/arrays for every
		// EntryPointMethod -- the main method's args, JUnit/keep roots, and any
		// open-programs entry points. Representative policy is the default.
		if (!(getBaseName(analysis.name) in ANALYSES_WITHOUT_MAIN)) {
			if (options.SOUND_MOCKING.value)
				cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/main/mocking/mocking-all-concrete-subtypes.dl")
			else
				cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/main/mocking/mocking-representative-subtypes.dl")
		}

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

	protected void assembleAnalysisStats(File analysis) {
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
			// temp
			if (options.FLOWLOG_ENGINE.value == false) cpp.includeAtEnd("$analysis", "${statsPath}/statistics.dl")
		}
	}

	@Override
	void processRelation(String query, Closure outputLineProcessor) {
		query = query.replaceAll(":", "_")
		def file = new File(this.outDir, "database/${query}.csv")
		if (!file.exists()) throw new FileNotFoundException(file.canonicalPath)
		file.eachLine { outputLineProcessor.call(it) }
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

		try {
			if (options.GENERATE_TAC.value) {
				TACGenerator.run(factsDir, new File(factsDir, "Methods.tac"))
			}
		} catch (Throwable t) {
			log.error "ERROR: TAC generation failed: ${t.message}"
		}
	}
}
