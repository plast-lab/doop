package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.utils.LBBuilder
import org.clyze.utils.Executor
import org.clyze.utils.FileOps
import org.clyze.utils.Helper

import static org.apache.commons.io.FileUtils.*

/**
 * A classic (may, unsound) DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 *
 * For supporting invocations over the web, the statistic step is broken into
 * two parts: (a) produce statistics and (b) print statistics.
 *
 * The run() method is the entry point. No other methods should be called directly by other classes.
 */
@CompileStatic
@InheritConstructors
@Log4j
class LB3Analysis extends DoopAnalysis {

	boolean isRefineStep
	LBBuilder lbBuilder

	@Override
	void run() {
		//Initialize the instance here and not in the constructor, in order to allow an analysis to be re-runnable.
		lbBuilder = new LBBuilder(cpp, outDir)
		database = new File("${database.canonicalPath}-lb")

		generateFacts()
		if (options.FACTS_ONLY.value) return

		initDatabase()
		mainAnalysis()

		try {
			FileOps.findFileOrThrow("${Doop.lbAnalysesPath}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
			reanalyze()
		}
		catch (e) {
			log.debug e.message
		}

		produceStats()
		cleanUp()

		log.info "\nAnalysis START"
		long t = Helper.timing {
			lbBuilder.invoke(options.BLOXBATCH.value as String, (options.BLOX_OPTS.value ?: '') as String, executor)
		}
		log.info "Analysis END\n"
		int dbSize = (sizeOfDirectory(database) / 1024).intValue()
		def cmdData = "Stats:Runtime(\"script wall-clock time (sec)\", $t). Stats:Runtime(\"disk footprint (KB)\", $dbSize)."
		def cmd = [options.BLOXBATCH.value as String, '-db', database as String, '-addBlock', cmdData as String]
		executor.execute(cmd, Executor.STDOUT_PRINTER)
	}

	void initDatabase() {
		def commonMacros = "${Doop.lbLogicPath}/commonMacros.logic"

		deleteQuietly(database)
		cpp.preprocess("${outDir}/flow-sensitive-schema.logic", "${Doop.lbLogicPath}/facts/flow-sensitive-schema.logic")
		cpp.preprocess("${outDir}/flow-insensitive-schema.logic", "${Doop.lbLogicPath}/facts/flow-insensitive-schema.logic")
		cpp.preprocess("${outDir}/import-entities.logic", "${Doop.lbLogicPath}/facts/import-entities.logic")
		cpp.preprocess("${outDir}/import-facts.logic", "${Doop.lbLogicPath}/facts/import-facts.logic")
		cpp.preprocess("${outDir}/to-flow-insensitive-delta.logic", "${Doop.lbLogicPath}/facts/to-flow-insensitive-delta.logic")
		cpp.preprocess("${outDir}/post-process.logic", "${Doop.lbLogicPath}/facts/post-process.logic", commonMacros)
		cpp.preprocess("${outDir}/mock-heap.logic", "${Doop.lbLogicPath}/facts/mock-heap.logic", commonMacros)

		lbBuilder
				.createDB(database.name)
				.timedTransaction("-- Init DB (import) --")
				.addBlockFile("flow-sensitive-schema.logic")
				.addBlockFile("flow-insensitive-schema.logic")
				.executeFile("import-entities.logic")
				.executeFile("import-facts.logic")


		if (options.TAMIFLEX.value) {
			def tamiflexDir = "${Doop.lbLogicPath}/addons/tamiflex"
			cpp.preprocess("${outDir}/tamiflex-fact-declarations.logic", "${tamiflexDir}/fact-declarations.logic")
			cpp.preprocess("${outDir}/tamiflex-import.logic", "${tamiflexDir}/import.logic")
			cpp.preprocess("${outDir}/tamiflex-post-import.logic", "${tamiflexDir}/post-import.logic")

			lbBuilder
					.addBlockFile("tamiflex-fact-declarations.logic")
					.executeFile("tamiflex-import.logic")
					.addBlockFile("tamiflex-post-import.logic")
		}

//        if (options.MAIN_CLASS.value)
//            lbBuilder.addBlock("""MainClass(x) <- ClassType(x), Type:Id(x:"${options.MAIN_CLASS.value}").""")

		lbBuilder
				.addBlock("""Stats:Runtime("fact generation time (sec)", $factGenTime).""")
				.commit()
				.elapsedTime()
				.timedTransaction("-- Init DB (post) --")
				.addBlockFile("post-process.logic")
				.addBlockFile("mock-heap.logic")
				.commit()
				.elapsedTime()
				.timedTransaction("-- Init DB (flow-ins) --")
				.executeFile("to-flow-insensitive-delta.logic")
				.commit()
				.elapsedTime()

		handleImportDynamicFacts()

		if (options.HEAPDLS.value || options.IMPORT_DYNAMIC_FACTS.value) {
			cpp.preprocess("${outDir}/import-dynamic-facts.logic", "${Doop.lbLogicPath}/facts/import-dynamic-facts.logic")
			cpp.preprocess("${outDir}/import-dynamic-facts2.logic", "${Doop.lbLogicPath}/facts/import-dynamic-facts2.logic")
			cpp.preprocess("${outDir}/externalheaps.logic", "${Doop.lbLogicPath}/facts/externalheaps.logic", commonMacros)
			lbBuilder
					.echo("-- Importing dynamic facts ---")
					.startTimer()
					.transaction()
					.executeFile("import-dynamic-facts.logic")
					.addBlockFile("externalheaps.logic")
					.commit().transaction()
					.executeFile("import-dynamic-facts2.logic")
					.commit()
					.elapsedTime()
		}

		if (options.TRANSFORM_INPUT.value)
			runTransformInput()
	}

	void mainAnalysis() {
		def commonMacros = "${Doop.lbLogicPath}/commonMacros.logic"
		cpp.preprocess("${outDir}/basic.logic", "${Doop.lbLogicPath}/basic/basic.logic", commonMacros)

		lbBuilder
				.timedTransaction("-- Basic Analysis --")
				.addBlockFile("basic.logic")

		if (options.CFG_ANALYSIS.value) {
			cpp.preprocess("${outDir}/cfg-analysis.logic", "${Doop.lbLogicPath}/addons/cfg-analysis/analysis.logic",
					"${Doop.lbLogicPath}/addons/cfg-analysis/declarations.logic")
			lbBuilder.addBlockFile("cfg-analysis.logic")
		}

		lbBuilder
				.commit()
				.elapsedTime()

		def macros = "${Doop.lbAnalysesPath}/${name}/macros.logic"
		def mainPath = "${Doop.lbLogicPath}/main"
		def analysisPath = "${Doop.lbAnalysesPath}/${name}"

		// By default, assume we run a context-sensitive analysis
		boolean isContextSensitive = true
		try {
			def file = FileOps.findFileOrThrow("${analysisPath}/analysis.properties", "No analysis.properties for ${name}")
			Properties props = FileOps.loadProperties(file)
			isContextSensitive = props.getProperty("is_context_sensitive").toBoolean()
		}
		catch (e) {
			log.debug e.message
		}
		if (isContextSensitive) {
			cpp.preprocessIfExists("${outDir}/${name}-declarations.logic", "${analysisPath}/declarations.logic",
					"${mainPath}/context-sensitivity-declarations.logic")
			cpp.preprocess("${outDir}/prologue.logic", "${mainPath}/prologue.logic", commonMacros)
			cpp.preprocessIfExists("${outDir}/${name}-delta.logic", "${analysisPath}/delta.logic",
					commonMacros, "${mainPath}/main-delta.logic")
			cpp.preprocess("${outDir}/${name}.logic", "${analysisPath}/analysis.logic",
					commonMacros, macros, "${mainPath}/context-sensitivity.logic")
		} else {
			cpp.preprocess("${outDir}/${name}-declarations.logic", "${analysisPath}/declarations.logic")
			cpp.preprocessIfExists("${outDir}/prologue.logic", "${mainPath}/prologue.logic", commonMacros)
			cpp.preprocessIfExists("${outDir}/${name}-prologue.logic", "${analysisPath}/prologue.logic")
			cpp.preprocessIfExists("${outDir}/${name}-delta.logic", "${analysisPath}/delta.logic")
			cpp.preprocess("${outDir}/${name}.logic", "${analysisPath}/analysis.logic")
		}

		lbBuilder
				.timedTransaction("-- Prologue --")
				.addBlockFile("${name}-declarations.logic")
				.addBlockFile("prologue.logic")
				.commit()
				.elapsedTime()
				.timedTransaction("-- Main Deltas -- ")
				.executeFile("${name}-delta.logic")

		if (options.REFLECTION.value) {
			cpp.preprocess("${outDir}/reflection-delta.logic", "${mainPath}/reflection/delta.logic")

			lbBuilder
					.commit()
					.transaction()
					.executeFile("reflection-delta.logic")
					.commit()
					.transaction()
		}

		/**
		 * Generic file for incrementally adding addons logic from various
		 * points. This is necessary in some cases to avoid weird errors from
		 * the engine (DELTA_RECURSION etc.) and in general it helps
		 * performance-wise.
		 */
		File addons = new File(outDir, "addons.logic")
		deleteQuietly(addons)
		touch(addons)

		String echo_analysis = "Pointer Analysis"

		if (options.INFORMATION_FLOW.value) {
			echo_analysis = "Pointer and Information-flow Analysis"
			def infoFlowPath = "${Doop.lbLogicPath}/addons/information-flow"
			cpp.preprocess("${outDir}/information-flow-declarations.logic", "${infoFlowPath}/declarations.logic")
			cpp.preprocess("${outDir}/information-flow-delta.logic", "${infoFlowPath}/delta.logic", macros)
			cpp.preprocess("${outDir}/information-flow-rules.logic", "${infoFlowPath}/rules.logic", macros)
			cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/information-flow-rules.logic")
			cpp.preprocess("${outDir}/sources-and-sinks.logic", "${infoFlowPath}/${options.INFORMATION_FLOW.value}${INFORMATION_FLOW_SUFFIX}.logic", macros)

			cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/sources-and-sinks.logic")

			lbBuilder
					.addBlockFile("information-flow-declarations.logic")
					.commit()
					.transaction()
					.executeFile("information-flow-delta.logic")
					.commit()
					.transaction()
		}

		if (options.X_IMPORT_PARTITIONS.value) {
			cpp.preprocess("${outDir}/addons.logic", options.X_IMPORT_PARTITIONS.value.toString())
		}

		String openProgramsRules = options.OPEN_PROGRAMS.value
		if (openProgramsRules) {
			log.debug "Using open-programs rules: ${openProgramsRules}"
			cpp.preprocess("${outDir}/open-programs.logic", "${Doop.lbLogicPath}/addons/open-programs/rules-${openProgramsRules}.logic", macros)
			cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/open-programs.logic")
		}

		if (options.DACAPO.value || options.DACAPO_BACH.value)
			cpp.includeAtStart("${outDir}/addons.logic", "${Doop.lbLogicPath}/addons/dacapo/rules.logic", commonMacros)

		if (options.TAMIFLEX.value) {
			cpp.preprocess("${outDir}/tamiflex-declarations.logic", "${Doop.lbLogicPath}/addons/tamiflex/declarations.logic")
			cpp.preprocess("${outDir}/tamiflex-delta.logic", "${Doop.lbLogicPath}/addons/tamiflex/delta.logic")
			cpp.includeAtStart("${outDir}/addons.logic", "${Doop.lbLogicPath}/addons/tamiflex/rules.logic", commonMacros)

			lbBuilder
					.addBlockFile("tamiflex-declarations.logic")
					.executeFile("tamiflex-delta.logic")
		}

		if (options.SANITY.value)
			cpp.includeAtStart("${outDir}/addons.logic", "${Doop.lbLogicPath}/addons/sanity.logic")

		cpp.includeAtStart("${outDir}/${name}.logic", "${outDir}/addons.logic")

		lbBuilder
				.commit()
				.elapsedTime()

		if (isRefineStep) importRefinement()

		lbBuilder
				.timedTransaction("-- " + echo_analysis + " --")
				.addBlockFile("${name}.logic")
				.commit()
				.elapsedTime()

		if (options.MUST.value) {
			cpp.preprocess("${outDir}/must-point-to-may-pre-analysis.logic", "${Doop.lbAnalysesPath}/must-point-to/may-pre-analysis.logic")
			cpp.preprocess("${outDir}/must-point-to.logic", "${Doop.lbAnalysesPath}/must-point-to/analysis-simple.logic")

			lbBuilder
					.echo("-- Pre Analysis (for Must) --")
					.startTimer()
					.transaction()
					.addBlockFile("must-point-to-may-pre-analysis.logic")
					.addBlock("RootMethodForMustAnalysis(?meth) <- Method:DeclaringType[?meth] = ?class, ApplicationClass(?class), Reachable(?meth).")
					.commit()
					.elapsedTime()
					.echo("-- Must Analysis --")
					.startTimer()
					.transaction()
					.addBlockFile("must-point-to.logic")
					.commit()
					.elapsedTime()
		}

		if (options.X_SERVER_LOGIC.value) {
			if (!options.FACTS_ONLY.value) {
				// Show a warning as recent changes may break old scripts
				// (e.g. removing LOGICBLOX_HOME as a deployment property).
				log.warn "WARNING: LB server logic is deprecated"
				cpp.preprocess("${outDir}/server.logic", "${Doop.lbLogicPath}/addons/server-logic/queries.logic")

				lbBuilder
						.timedTransaction("-- Server Logic --")
						.addBlockFile("server.logic")
						.commit()
						.elapsedTime()
			} else {
				log.warn "WARNING: LB server logic is ignored when using --${options.FACTS_ONLY.name}"
			}
		}

		if (options.EXTRA_LOGIC.value) {
			for (String extraLogic : options.EXTRA_LOGIC.value as Collection<String>) {
				// Safety: check file extension to avoid using this mechanism
				// to read files from anywhere in the system.
				if (extraLogic.endsWith(".logic"))
					lbBuilder.include(extraLogic as String)
				else
					log.warn "WARNING: Ignoring file not ending in .logic: ${extraLogic}"
			}
		}
	}

	void produceStats() {
		if (options.X_STATS_NONE.value) return

		def specialStatsScript = new File("${Doop.lbAnalysesPath}/${name}/statistics.part.lb")
		if (specialStatsScript.exists()) {
			lbBuilder.include(specialStatsScript.toString())
			return
		}

		def macros = "${Doop.lbAnalysesPath}/${name}/macros.logic"
		def statsPath = "${Doop.lbLogicPath}/addons/statistics"
		cpp.preprocess("${outDir}/statistics-simple.logic", "${statsPath}/statistics-simple.logic", macros)

		lbBuilder
				.timedTransaction("-- Statistics --")
				.addBlockFile("statistics-simple.logic")

		if (options.X_STATS_FULL.value) {
			cpp.preprocess("${outDir}/statistics.logic", "${statsPath}/statistics.logic", macros)
			lbBuilder.addBlockFile("statistics.logic")
		}

		lbBuilder
				.commit()
				.elapsedTime()
	}

	void cleanUp() {
		String home = System.getenv('HOME')
		if (home) {
			File f = new File(home)
			// Delete test home directory.
			if (f.name.contains('lb-test-home-dir')) {
				log.debug "Deleting temporary home directory: ${f.canonicalPath}"
				f.delete()
			}
		}
	}

	void runTransformInput() {
		cpp.preprocess("${outDir}/transform.logic", "${Doop.lbLogicPath}/addons/transform/rules.logic", "${Doop.lbLogicPath}/addons/transform/declarations.logic")
		lbBuilder
				.echo("-- Transforming Facts --")
				.startTimer()
				.transaction()
				.addBlockFile("${outDir}/transform.logic")
				.commit()

		2.times { int i ->
			lbBuilder
					.echo(""" "-- Transformation (step $i) --" """)
					.transaction()
					.executeFile("${Doop.lbLogicPath}/addons/transform/delta.logic")
					.commit()
		}
		lbBuilder.elapsedTime()
	}

	private void reanalyze() {
		cpp.preprocess("${outDir}/refinement-delta.logic", "${Doop.lbAnalysesPath}/${name}/refinement-delta.logic")
		cpp.preprocess("${outDir}/export-refinement.logic", "${Doop.lbLogicPath}/main/export-refinement.logic")
		cpp.preprocess("${outDir}/import-refinement.logic", "${Doop.lbLogicPath}/main/import-refinement.logic")

		lbBuilder
				.echo("++++ Refinement ++++")
				.echo("-- Export --")
				.startTimer()
				.transaction()
				.executeFile("refinement-delta.logic")
				.commit()
				.transaction()
				.executeFile("export-refinement.logic")
				.commit()
				.elapsedTime()

		isRefineStep = true
		initDatabase()
		mainAnalysis()
	}

	private void importRefinement() {
		lbBuilder
				.echo("-- Import --")
				.startTimer()
				.transaction()
				.executeFile("import-refinement.logic")
				.commit()
				.elapsedTime()
	}

	@Override
	void processRelation(String relation, Closure outputLineProcessor) {
		def cmd = [options.BLOXBATCH.value as String, '-db', database as String, '-query', relation]
		executor.execute(cmd, outputLineProcessor)
	}
}
