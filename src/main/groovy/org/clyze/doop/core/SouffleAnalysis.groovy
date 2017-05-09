package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.system.CheckSum
import org.clyze.doop.system.FileOps
import org.clyze.doop.input.InputResolutionContext

import static org.apache.commons.io.FileUtils.*

@CompileStatic
@TypeChecked
class SouffleAnalysis extends DoopAnalysis {

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


        copyDirectoryToDirectory(new File(Doop.souffleLogicPath + File.separator + "facts"), outDir)
        initDatabase()
        basicAnalysis()
        mainAnalysis()
        produceStats()
        runSouffle(Integer.parseInt(options.JOBS.value.toString()), factsDir, outDir)
    }

    @Override
    protected void initDatabase() {
        cpp.preprocess("${outDir}/${name}.dl", "${Doop.souffleLogicPath}/facts/to-flow-sensitive.dl",
                                               "${Doop.souffleLogicPath}/facts/flow-sensitive-schema.dl",
                                               "${Doop.souffleLogicPath}/facts/flow-insensitive-schema.dl",
                                               "${Doop.souffleLogicPath}/facts/import-entities.dl",
                                               "${Doop.souffleLogicPath}/facts/import-facts.dl",
                                               "${Doop.souffleLogicPath}/facts/post-process.dl",
                                               "${Doop.souffleLogicPath}/facts/mock-heap.dl",
                                               "${Doop.souffleLogicPath}/facts/export.dl")

        if (options.TAMIFLEX.value) {
            def tamiflexDir = "${Doop.souffleAddonsPath}/tamiflex"
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/fact-declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/import.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/post-import.dl")
        }
    }

    @Override
    protected void basicAnalysis() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleLogicPath}/basic/basic.dl", commonMacros)

        if (options.CFG_ANALYSIS.value || name == "sound-may-point-to") {
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/cfg-analysis/analysis.dl",
                    "${Doop.souffleAddonsPath}/cfg-analysis/declarations.dl")
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
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/string-constants.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/exceptions.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", /*"${analysisPath}/declarations.dl",*/
                    "${mainPath}/context-sensitivity-declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl")
        }
        else {
            if (isContextSensitive) {
                cpp.includeAtStart("${outDir}/${name}.dl", /*"${analysisPath}/declarations.dl",*/
                        "${mainPath}/context-sensitivity-declarations.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/delta.dl",
                        commonMacros, "${mainPath}/main-delta.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl",
                        commonMacros, macros, "${mainPath}/context-sensitivity.dl")
            } else {
                cpp.includeAtStart("${outDir}/${name}-declarations.dl", "${analysisPath}/declarations.dl")
                cpp.includeAtStart("${outDir}/prologue.dl", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtStart("${outDir}/${name}-prologue.dl", "${analysisPath}/prologue.dl")
                cpp.includeAtStart("${outDir}/${name}-delta.dl", "${analysisPath}/delta.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl")
            }

        }
        if (options.REFLECTION.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/reflection/delta.dl")
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value)
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/dacapo/rules.dl", commonMacros)

        if (options.TAMIFLEX.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/delta.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/rules.dl", commonMacros)
        }

        if (options.SANITY.value)
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/sanity.dl")

        if (options.MAIN_CLASS.value) {
            def analysisFile = FileOps.findFileOrThrow("${outDir}/${name}.dl", "Missing ${outDir}/${name}.dl")
            analysisFile.append("""MainClass("${options.MAIN_CLASS.value}").\n""")
        }
    }

    private void runSouffle(int jobs, File factsDir, File outDir) {
        def analysisFileChecksum = CheckSum.checksum(new File("${outDir}/${name}.dl"), DoopAnalysisFactory.HASH_ALGO)
        def analysisChecksum = CheckSum.checksum(analysisFileChecksum + String.valueOf(options.SOUFFLE_PROFILE.value),
                                                 DoopAnalysisFactory.HASH_ALGO)
        def analysisCacheDir = new File("${Doop.souffleAnalysesCache}/${analysisChecksum}")

        if (!analysisCacheDir.exists() || options.SOUFFLE_DEBUG.value) {
            def compilationCommand = "souffle -c -o ${outDir}/${name} ${outDir}/${name}.dl"

            if (options.SOUFFLE_PROFILE.value)
                compilationCommand += " -p$outDir.absolutePath/profile.txt"
            if (options.SOUFFLE_DEBUG.value)
                compilationCommand += " -r$outDir.absolutePath/report.html"

            logger.info "Compiling datalog to produce C++ program and executable with souffle"
            logger.info "Souffle command: ${compilationCommand}"

            deleteQuietly(analysisCacheDir)
            analysisCacheDir.mkdirs()

            // Create a subshell to temporarely cd to the analysis cache directory and execute the compilation
            // command, as the analysis executable is created at the directory level of the command's invocation.
            def subshellCommand = "(cd ${analysisCacheDir} && " + compilationCommand + ")"

            def ignoreCounter = 0
            long t = timing {
                executor.execute(subshellCommand) { String line ->
                    if (ignoreCounter != 0) ignoreCounter--
                    else if (line.startsWith("Warning: No rules/facts defined for relation") ||
                             line.startsWith("Warning: Deprecated output qualifier was used")) {
                        println line
                        ignoreCounter = 2
                    }
                    else if (line.startsWith("Warning: Record types in output relations are not printed verbatim")) ignoreCounter = 2
                    else println line
                }
            }

            logger.info "Compilation time (sec): ${t}"
            logger.info "Running analysis executable"
        }
        else {
            logger.info "Running cached analysis executable"
        }

        deleteQuietly(database)
        database.mkdirs()

        def executionCommand = "${analysisCacheDir}/${name} -j$jobs -F$factsDir -D$database"

        logger.info executionCommand
        long t = timing { executor.execute(executionCommand) }
        logger.info "Analysis execution time (sec): ${t}"
    }

    @Override
    protected void produceStats() {
        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
        }
        // Special case of X_STATS_AROUND (detected automatically)
        def specialStats       = new File("${Doop.souffleAnalysesPath}/${name}/statistics.dl")
        if (specialStats.exists()) {
            cpp.includeAtStart("${outDir}/${name}.dl", specialStats.toString())
            return
        }

        def macros    = "${Doop.souffleAnalysesPath}/${name}/macros.dl"
        def statsPath = "${Doop.souffleAddonsPath}/statistics"
        cpp.includeAtStart("${outDir}/${name}.dl", "${statsPath}/statistics-simple.dl", macros)

        if (options.X_STATS_FULL.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${statsPath}/statistics.dl", macros)
        }
    }

    @Override
    protected void runTransformInput() {}
}
