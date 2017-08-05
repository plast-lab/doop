package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.input.InputResolutionContext
import org.clyze.utils.CheckSum
import org.clyze.utils.FileOps
import org.clyze.utils.Helper

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory

@CompileStatic
@TypeChecked
class SouffleAnalysis extends DoopAnalysis {

    /**
     * The analysis logic file
     */
    File analysis

    /**
     * The cache dir for the analysis executable
     */
    File souffleAnalysesCache

    /**
     * Total time for Souffle compilation phase
     */
    protected long compilationTime

    /**
     * Total time for analysis execution
     */
    protected long executionTime
    File souffleAnalysisCacheFile

    protected SouffleAnalysis(String id,
                              String name,
                              Map<String, AnalysisOption> options,
                              InputResolutionContext ctx,
                              File outDir,
                              File cacheDir,
                              List<File> inputFiles,
                              List<File> platformLibs,
                              Map<String, String> commandsEnvironment) {
        super(id, name, options, ctx, outDir, cacheDir, inputFiles, platformLibs, commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }
    }

    @Override
    void run() {
        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return

        analysis = new File(outDir, "${name}.dl")
        deleteQuietly(analysis)
        analysis.createNewFile()

        initDatabase()
        basicAnalysis()
        mainAnalysis()
        if (!options.X_SERVER_LOGIC.value)
            produceStats()

        compileAnalysis()
        executeAnalysis(options.SOUFFLE_JOBS.value as Integer)

        if (!options.X_SERVER_LOGIC.value) {
            int dbSize = (sizeOfDirectory(database) / 1024).intValue()
            File runtimeMetricsFile = new File(database, "Stats_Runtime.csv")
            runtimeMetricsFile.createNewFile()
            runtimeMetricsFile.append("analysis compilation time (sec)\t$compilationTime\n")
            runtimeMetricsFile.append("analysis execution time (sec)\t$executionTime\n")
            runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
            runtimeMetricsFile.append("soot-fact-generation time (sec)\t$sootTime\n")
        }
    }

    @Override
    protected void initDatabase() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/flow-sensitive-schema.dl")
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/flow-insensitive-schema.dl")
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/import-entities.dl")
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/import-facts.dl")
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/to-flow-sensitive.dl")
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/post-process.dl", commonMacros)
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/mock-heap.dl", commonMacros)
        cpp.includeAtEnd("$analysis", "${Doop.souffleFactsPath}/export.dl")

        if (options.TAMIFLEX.value) {
            def tamiflexPath = "${Doop.souffleAddonsPath}/tamiflex"
            cpp.includeAtEnd("$analysis", "${tamiflexPath}/fact-declarations.dl")
            cpp.includeAtEnd("$analysis", "${tamiflexPath}/import.dl")
            cpp.includeAtEnd("$analysis", "${tamiflexPath}/post-import.dl")
        }
    }

    @Override
    protected void basicAnalysis() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/basic.dl", commonMacros)

        if (options.CFG_ANALYSIS.value || name == "sound-may-point-to") {
            def cfgAnalysisPath = "${Doop.souffleAddonsPath}/cfg-analysis"
            cpp.includeAtEnd("$analysis", "${cfgAnalysisPath}/analysis.dl", "${cfgAnalysisPath}/declarations.dl")
        }
    }

    @Override
    protected void mainAnalysis() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        def macros       = "${Doop.souffleAnalysesPath}/${name}/macros.dl"
        def mainPath     = "${Doop.souffleLogicPath}/main"
        def analysisPath = "${Doop.souffleAnalysesPath}/${name}"

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

        if (name == "sound-may-point-to") {
            cpp.includeAtEnd("$analysis", "${mainPath}/string-constants.dl")
            cpp.includeAtEnd("$analysis", "${mainPath}/exceptions.dl")
            cpp.includeAtEndIfExists("$analysis", "${analysisPath}/declarations.dl",
                    "${mainPath}/context-sensitivity-declarations.dl")
            cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl")
        }
        else {
            if (isContextSensitive) {
                cpp.includeAtEndIfExists("$analysis", "${analysisPath}/declarations.dl",
                        "${mainPath}/context-sensitivity-declarations.dl")
                cpp.includeAtEnd("$analysis", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtEndIfExists("$analysis", "${analysisPath}/delta.dl",
                        commonMacros, "${mainPath}/main-delta.dl")
                cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl",
                        commonMacros, macros, "${mainPath}/context-sensitivity.dl")
            } else {
                cpp.includeAtEnd("$analysis", "${analysisPath}/declarations.dl")
                cpp.includeAtEndIfExists("$analysis", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtEndIfExists("$analysis", "${analysisPath}/prologue.dl")
                cpp.includeAtEndIfExists("$analysis", "${analysisPath}/delta.dl")
                cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl")
            }

        }

        if (options.REFLECTION.value) {
            cpp.includeAtEnd("$analysis", "${mainPath}/reflection/delta.dl")
        }

        if (options.INFORMATION_FLOW.value || options.MINIMAL_INFORMATION_FLOW.value) {
            def infoFlowPath = "${Doop.souffleAddonsPath}/information-flow"
            cpp.includeAtEnd("$analysis", "${infoFlowPath}/declarations.dl")
            cpp.includeAtEnd("$analysis", "${infoFlowPath}/delta.dl", macros)
            cpp.includeAtEnd("$analysis", "${infoFlowPath}/rules.dl", macros)

            if (options.MINIMAL_INFORMATION_FLOW.value) {
                cpp.includeAtEnd("$analysis", "${infoFlowPath}/minimal-sources-and-sinks.dl", macros)
            } else {
                cpp.includeAtEnd("$analysis", "${infoFlowPath}/${options.INFORMATION_FLOW.value}-sources-and-sinks.dl", macros)
            }
        }

        if (options.OPEN_PROGRAMS.value) {
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/open-programs/rules-${options.OPEN_PROGRAMS.value}.dl", macros, commonMacros)
        } else {
            // This needs cleaning up. We are including one version by default, but distinguishing
            // inside the file (using #ifdefs) whether we are in OPEN_PROGRAMS mode or not.
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/open-programs/rules-concrete-types.dl", macros, commonMacros)
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value)
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/dacapo/rules.dl", commonMacros)

        if (options.TAMIFLEX.value) {
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/tamiflex/declarations.dl")
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/tamiflex/delta.dl")
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/tamiflex/rules.dl", commonMacros)
        }

        if (options.SANITY.value)
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/sanity.dl")

        if (options.MAIN_CLASS.value) {
            def analysisFile = FileOps.findFileOrThrow("$analysis", "Missing $analysis")
            analysisFile.append("""MainClass("${options.MAIN_CLASS.value}").\n""")
        }

        if (!options.X_STOP_AT_FACTS.value && options.X_SERVER_LOGIC.value) {
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/server-logic/queries.dl")
        }

        if (options.GENERATE_PROGUARD_KEEP_DIRECTIVES.value) {
            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/proguard/keep.dl")
        }
    }

    private void compileAnalysis() {
        def analysisFileChecksum = CheckSum.checksum(analysis, DoopAnalysisFactory.HASH_ALGO)
        def stringToHash = analysisFileChecksum + options.SOUFFLE_PROFILE.value.toString()
        def analysisChecksum = CheckSum.checksum(stringToHash, DoopAnalysisFactory.HASH_ALGO)
        souffleAnalysesCache = new File("${Doop.souffleAnalysesCache}")
        souffleAnalysesCache.mkdirs()
        souffleAnalysisCacheFile = new File("${Doop.souffleAnalysesCache}/${analysisChecksum}")

        if (!souffleAnalysisCacheFile.exists() || options.SOUFFLE_DEBUG.value) {
            def compilationCommand = "souffle -c -o ${outDir}/${name} $analysis"

            if (options.SOUFFLE_PROFILE.value)
                compilationCommand += " -p${outDir}/profile.txt"
            if (options.SOUFFLE_DEBUG.value)
                compilationCommand += " -r${outDir}/report.html"

            logger.info "Compiling Datalog to C++ program and executable"
            logger.debug "Compilation command: $compilationCommand"

            // Create a subshell to temporarely cd to the analysis cache directory and execute the compilation
            // command, as the analysis executable is created at the directory level of the command's invocation.
            def subshellCommand = "(cd ${souffleAnalysesCache} && ${compilationCommand} && cp ${outDir}/${name} ${souffleAnalysisCacheFile.canonicalPath} 2>/dev/null" + ")"

            logger.debug "Setup subshell command: $subshellCommand"

            def ignoreCounter = 0
            compilationTime = Helper.timing {
                executor.execute(subshellCommand) { String line ->
                    if (ignoreCounter != 0) ignoreCounter--
                    else if (line.startsWith("Warning: No rules/facts defined for relation") ||
                            line.startsWith("Warning: Deprecated output qualifier was used")) {
                        logger.info line
                        ignoreCounter = 2
                    }
                    else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
                    else logger.info line
                }
            }

            logger.info "Analysis compilation time (sec): $compilationTime"
            logger.info "Caching analysis executable in $souffleAnalysesCache"
        }
        else {
            logger.info "Using cached analysis executable ${souffleAnalysesCache}"
        }
    }

    private void executeAnalysis(int jobs) {
        deleteQuietly(database)
        database.mkdirs()

        def executionCommand = "${souffleAnalysisCacheFile} -j$jobs -F$factsDir -D$database"
        if (options.SOUFFLE_PROFILE.value)
            executionCommand += " -p${outDir}/profile.txt"

        logger.debug "Execution command: $executionCommand"
        logger.info "Running analysis"
        executionTime = Helper.timing { executor.execute(executionCommand) }
        logger.info "Analysis execution time (sec): $executionTime"
    }

    @Override
    protected void produceStats() {
        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
        }
        // Special case of X_STATS_AROUND (detected automatically)
        def specialStats       = new File("${Doop.souffleAnalysesPath}/${name}/statistics.dl")
        if (specialStats.exists()) {
            cpp.includeAtEnd("$analysis", specialStats.toString())
            return
        }

        def macros    = "${Doop.souffleAnalysesPath}/${name}/macros.dl"
        def statsPath = "${Doop.souffleAddonsPath}/statistics"
        cpp.includeAtEnd("$analysis", "${statsPath}/statistics-simple.dl", macros)

        if (options.X_STATS_FULL.value) {
            cpp.includeAtEnd("$analysis", "${statsPath}/statistics.dl", macros)
        }
    }

    @Override
    protected void runTransformInput() {}

    @Override
    void processRelation(String query, Closure outputLineProcessor) {
        query = query.replaceAll(":", "_")
        def file = new File(this.outDir, "database/${query}.csv")
        if (!file.exists()) throw new FileNotFoundException(file.canonicalPath)
        file.eachLine { outputLineProcessor.call(it.replaceAll("\t", ", ")) }
    }
}
