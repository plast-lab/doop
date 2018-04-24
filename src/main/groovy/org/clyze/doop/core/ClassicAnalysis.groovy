package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.LBBuilder
import org.clyze.doop.input.InputResolutionContext
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
@TypeChecked
class ClassicAnalysis extends DoopAnalysis {

    boolean isRefineStep

    protected ClassicAnalysis(String id,
                              String name,
                              Map<String, AnalysisOption> options,
                              InputResolutionContext ctx,
                              File outDir,
                              File cacheDir,
                              List<File> inputFiles,
                              List<File> libraryFiles,
                              List<File> heapFiles,
                              List<File> platformLibs,
                              Map<String, String> commandsEnvironment) {
        super(id, name, options, ctx, outDir, cacheDir, inputFiles, libraryFiles, heapFiles, platformLibs, commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }
    }

    @Override
    void run() {
        //Initialize the instance here and not in the constructor, in order to allow an analysis to be re-runnable.
        lbBuilder = new LBBuilder(cpp, outDir)

        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return

        initDatabase()
        if (!options.X_STOP_AT_INIT.value) {

            basicAnalysis()
            if (!options.X_STOP_AT_BASIC.value) {

                mainAnalysis()

                try {
                    FileOps.findFileOrThrow("${Doop.analysesPath}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
                    reanalyze()
                }
                catch(e) {
                    logger.debug e.getMessage()
                }

                produceStats()
            }
        }

        logger.info "\nAnalysis START"
        long t = Helper.timing { lbBuilder.invoke(logger, options.BLOXBATCH.value as String, (options.BLOX_OPTS.value ?: '') as String, executor) }
        logger.info "Analysis END\n"
        int dbSize = (sizeOfDirectory(database) / 1024).intValue()
        def cmdData = "Stats:Runtime(\"script wall-clock time (sec)\", $t). Stats:Runtime(\"disk footprint (KB)\", $dbSize)."
        def cmd = [options.BLOXBATCH.value as String, '-db', database as String, '-addBlock', cmdData as String]
        executor.execute(outDir as String, cmd, Executor.STDOUT_PRINTER)
    }

    @Override
    protected void initDatabase() {
        def commonMacros = "${Doop.logicPath}/commonMacros.logic"

        deleteQuietly(database)
        cpp.preprocess("${outDir}/flow-sensitive-schema.logic", "${Doop.factsPath}/flow-sensitive-schema.logic")
        cpp.preprocess("${outDir}/flow-insensitive-schema.logic", "${Doop.factsPath}/flow-insensitive-schema.logic")
        cpp.preprocess("${outDir}/import-entities.logic", "${Doop.factsPath}/import-entities.logic")
        cpp.preprocess("${outDir}/import-facts.logic", "${Doop.factsPath}/import-facts.logic")
        cpp.preprocess("${outDir}/to-flow-insensitive-delta.logic", "${Doop.factsPath}/to-flow-insensitive-delta.logic")
        cpp.preprocess("${outDir}/post-process.logic", "${Doop.factsPath}/post-process.logic", commonMacros)
        cpp.preprocess("${outDir}/mock-heap.logic", "${Doop.factsPath}/mock-heap.logic", commonMacros)

        lbBuilder
                .createDB(database.getName())
                .timedTransaction("-- Init DB (import) --")
                .addBlockFile("flow-sensitive-schema.logic")
                .addBlockFile("flow-insensitive-schema.logic")
                .executeFile("import-entities.logic")
                .executeFile("import-facts.logic")


        if (options.TAMIFLEX.value) {
            def tamiflexDir = "${Doop.addonsPath}/tamiflex"
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
                .addBlock("""Stats:Runtime("soot-fact-generation time (sec)", $sootTime).""")
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

        if (options.HEAPDL.value || options.IMPORT_DYNAMIC_FACTS.value) {
            cpp.preprocess("${outDir}/import-dynamic-facts.logic", "${Doop.factsPath}/import-dynamic-facts.logic")
            cpp.preprocess("${outDir}/import-dynamic-facts2.logic", "${Doop.factsPath}/import-dynamic-facts2.logic")
            cpp.preprocess("${outDir}/externalheaps.logic", "${Doop.factsPath}/externalheaps.logic", commonMacros)
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

    @Override
    protected void basicAnalysis() {
        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            dynFiles.eachWithIndex { String dynFile, Integer index ->
                File f = new File(dynFile)
                File dynImport = new File(outDir, "dynamic${index}.import")
                FileOps.writeToFile dynImport, """\
                                              option,delimiter,"\t"
                                              option,hasColumnNames,false

                                              fromFile,"${f.getCanonicalPath()}",a,inv,b,type
                                              toPredicate,Config:DynamicClass,type,inv
                                              """.toString().stripIndent()

                lbBuilder.eval("import -f $dynImport")
            }
        }

        def commonMacros = "${Doop.logicPath}/commonMacros.logic"
        cpp.preprocess("${outDir}/basic.logic", "${Doop.logicPath}/basic/basic.logic", commonMacros)

        lbBuilder
                .timedTransaction("-- Basic Analysis --")
                .addBlockFile("basic.logic")

        if (options.CFG_ANALYSIS.value) {
            cpp.preprocess("${outDir}/cfg-analysis.logic", "${Doop.addonsPath}/cfg-analysis/analysis.logic",
                    "${Doop.addonsPath}/cfg-analysis/declarations.logic")
            lbBuilder.addBlockFile("cfg-analysis.logic")
        }

        lbBuilder
                .commit()
                .elapsedTime()
    }

    @Override
    protected void mainAnalysis() {
        def commonMacros = "${Doop.logicPath}/commonMacros.logic"
        def macros       = "${Doop.analysesPath}/${name}/macros.logic"
        def mainPath     = "${Doop.logicPath}/main"
        def analysisPath = "${Doop.analysesPath}/${name}"

        // By default, assume we run a context-sensitive analysis
        boolean isContextSensitive = true
        try {
            def file = FileOps.findFileOrThrow("${analysisPath}/analysis.properties", "No analysis.properties for ${name}")
            Properties props = FileOps.loadProperties(file)
            isContextSensitive = props.getProperty("is_context_sensitive").toBoolean()
        }
        catch(e) {
            logger.debug e.getMessage()
        }
        if (isContextSensitive) {
            cpp.preprocessIfExists("${outDir}/${name}-declarations.logic", "${analysisPath}/declarations.logic",
                    "${mainPath}/context-sensitivity-declarations.logic")
            cpp.preprocess("${outDir}/prologue.logic", "${mainPath}/prologue.logic", commonMacros)
            cpp.preprocessIfExists("${outDir}/${name}-delta.logic", "${analysisPath}/delta.logic",
                    commonMacros, "${mainPath}/main-delta.logic")
            cpp.preprocess("${outDir}/${name}.logic", "${analysisPath}/analysis.logic",
                    commonMacros, macros, "${mainPath}/context-sensitivity.logic")
        }
        else {
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
                .timedTransaction("-- Wala Deltas -- ")
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
            def infoFlowPath = "${Doop.addonsPath}/information-flow"
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

        if (options.IMPORT_PARTITIONS.value) {
            cpp.preprocess("${outDir}/addons.logic", options.IMPORT_PARTITIONS.value.toString())
        }

        if (!options.MAIN_CLASS && !options.TAMIFLEX && !options.HEAPDL && !options.ANDROID && !options.DACAPO && !options.DACAPO_BACH) {
            warnOpenPrograms()
            if (options.OPEN_PROGRAMS.value)
                cpp.preprocess("${outDir}/open-programs.logic", "${Doop.addonsPath}/open-programs/rules-${options.OPEN_PROGRAMS.value}.logic", macros)
            else
                cpp.preprocess("${outDir}/open-programs.logic", "${Doop.addonsPath}/open-programs/rules-concrete-types.logic", macros)
            cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/open-programs.logic")
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value)
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/dacapo/rules.logic", commonMacros)

        if (options.TAMIFLEX.value) {
            cpp.preprocess("${outDir}/tamiflex-declarations.logic", "${Doop.addonsPath}/tamiflex/declarations.logic")
            cpp.preprocess("${outDir}/tamiflex-delta.logic", "${Doop.addonsPath}/tamiflex/delta.logic")
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/tamiflex/rules.logic", commonMacros)

            lbBuilder
                    .addBlockFile("tamiflex-declarations.logic")
                    .executeFile("tamiflex-delta.logic")
        }

        if (options.SANITY.value)
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/sanity.logic")

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
            cpp.preprocess("${outDir}/must-point-to-may-pre-analysis.logic", "${Doop.analysesPath}/must-point-to/may-pre-analysis.logic")
            cpp.preprocess("${outDir}/must-point-to.logic", "${Doop.analysesPath}/must-point-to/analysis-simple.logic")

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
            if (!options.X_STOP_AT_FACTS.value) {
                // Show a warning as recent changes may break old scripts
                // (e.g. removing LOGICBLOX_HOME as a deployment property).
                logger.warn "WARNING: LB server logic is deprecated"
                cpp.preprocess("${outDir}/server.logic", "${Doop.addonsPath}/server-logic/queries.logic")

                lbBuilder
                .timedTransaction("-- Server Logic --")
                .addBlockFile("server.logic")
                .commit()
                .elapsedTime()
            } else {
                logger.warn "WARNING: LB server logic is ignored when using --${options.X_STOP_AT_FACTS.name}"
            }
        }

        if (options.X_EXTRA_LOGIC.value) {
            logger.warn "WARNING: the LB mode does not support --${options.X_EXTRA_LOGIC.name}"
        }
    }

    @Override
    protected void produceStats() {
        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
            lbBuilder.include(options.X_STATS_AROUND.value as String)
            return
        }

        // Special case of X_STATS_AROUND (detected automatically)
        def specialStatsScript = new File("${Doop.analysesPath}/${name}/statistics.part.lb")
        if (specialStatsScript.exists()) {
            lbBuilder.include(specialStatsScript.toString())
            return
        }

        def macros    = "${Doop.analysesPath}/${name}/macros.logic"
        def statsPath = "${Doop.addonsPath}/statistics"
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

    @Override
    protected void runTransformInput() {
        cpp.preprocess("${outDir}/transform.logic", "${Doop.addonsPath}/transform/rules.logic", "${Doop.addonsPath}/transform/declarations.logic")
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
                    .executeFile("${Doop.addonsPath}/transform/delta.logic")
                    .commit()
        }
        lbBuilder.elapsedTime()
    }

    private void reanalyze() {
        cpp.preprocess("${outDir}/refinement-delta.logic", "${Doop.analysesPath}/${name}/refinement-delta.logic")
        cpp.preprocess("${outDir}/export-refinement.logic", "${Doop.logicPath}/main/export-refinement.logic")
        cpp.preprocess("${outDir}/import-refinement.logic", "${Doop.logicPath}/main/import-refinement.logic")

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
        basicAnalysis()
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
        executor.execute(outDir as String, cmd, outputLineProcessor)
    }
}
