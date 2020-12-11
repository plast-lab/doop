package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory

@CompileStatic
@InheritConstructors
@Log4j
class SoufflePythonAnalysis extends SouffleAnalysis {

    File analysis

    @Override
    void run() {
        generateFacts()

        if (options.X_STOP_AT_FACTS.value) return

        File analysis = new File(outDir, "${name}.dl")
        deleteQuietly(analysis)
        analysis.createNewFile()

        initDatabase(analysis)
        basicAnalysis(analysis)
        if (!options.X_STOP_AT_BASIC.value) {
            runAnalysisAndProduceStats(analysis)
        }

        def script = newScriptForAnalysis(executor)
        if (options.SOUFFLE_RUN_INTERPRETED.value) {
            script.interpretScript(analysis, outDir, factsDir,
                    options.SOUFFLE_JOBS.value as int,
                    options.SOUFFLE_PROFILE.value as boolean,
                    options.SOUFFLE_DEBUG.value as boolean,
                    options.X_CONTEXT_REMOVER.value as boolean)
        } else {
            def generatedFile = script.compile(analysis, outDir,
                    options.SOUFFLE_PROFILE.value as boolean,
                    options.SOUFFLE_DEBUG.value as boolean,
                    options.SOUFFLE_PROVENANCE.value as boolean,
                    options.SOUFFLE_LIVE_PROFILE.value as boolean,
                    options.SOUFFLE_FORCE_RECOMPILE.value as boolean,
                    options.X_CONTEXT_REMOVER.value as boolean)

            script.run(generatedFile, factsDir, outDir,
                    options.SOUFFLE_JOBS.value as int,
                    (options.X_MONITORING_INTERVAL.value as long) * 1000,
                    monitorClosure,
                    options.SOUFFLE_PROVENANCE.value as boolean,
                    options.SOUFFLE_LIVE_PROFILE.value as boolean)

        }

        int dbSize = (sizeOfDirectory(database) / 1024).intValue()
        File runtimeMetricsFile = new File(database, "Stats_Runtime.csv")
        runtimeMetricsFile.createNewFile()
        runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
        runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
        runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
        runtimeMetricsFile.append("wala-fact-generation time (sec)\t$factGenTime\n")

    }

    @Override
    void initDatabase(File analysis) {
        cpp.includeAtEnd("$analysis", "${Doop.soufflePythonPath}/facts/schema.dl")
        cpp.includeAtEnd("$analysis", "${Doop.soufflePythonPath}/facts/import-entities.dl")
        cpp.includeAtEnd("$analysis", "${Doop.soufflePythonPath}/facts/import-facts.dl")
        cpp.includeAtEnd("$analysis", "${Doop.soufflePythonPath}/facts/post-process.dl")
    }

    @Override
    void basicAnalysis(File analysis) {
//        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
//        cpp.includeAtEnd("$analysis", "${Doop.souffleLogicPath}/basic/basic.dl", commonMacros)
//
//        if (options.CFG_ANALYSIS.value || name == "sound-may-point-to") {
//            def cfgAnalysisPath = "${Doop.souffleAddonsPath}/cfg-analysis"
//            cpp.includeAtEnd("$analysis", "${cfgAnalysisPath}/analysis.dl", "${cfgAnalysisPath}/declarations.dl")
//        }
    }

    @Override
    void mainAnalysis(File analysis) {
        cpp.includeAtEnd("$analysis", "${Doop.soufflePythonAnalysesPath}/${name}/analysis.dl")
//        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
//        def mainPath = "${Doop.souffleLogicPath}/main"
//        def analysisPath = "${Doop.souffleAnalysesPath}/${name}"
//
//        cpp.includeAtEndIfExists("$analysis", "${analysisPath}/declarations.dl")
//        cpp.includeAtEndIfExists("$analysis", "${analysisPath}/delta.dl", commonMacros)
//        cpp.includeAtEnd("$analysis", "${analysisPath}/analysis.dl", commonMacros)
//
//
//        if (options.INFORMATION_FLOW.value) {
//            def infoFlowPath = "${Doop.souffleAddonsPath}/information-flow"
//            cpp.includeAtEnd("$analysis", "${infoFlowPath}/declarations.dl")
//            cpp.includeAtEnd("$analysis", "${infoFlowPath}/delta.dl")
//            cpp.includeAtEnd("$analysis", "${infoFlowPath}/rules.dl")
//            cpp.includeAtEnd("$analysis", "${infoFlowPath}/${options.INFORMATION_FLOW.value}${INFORMATION_FLOW_SUFFIX}.dl")
//        }
//
//
//        if (options.SANITY.value)
//            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/sanity.dl")
//
//        if (!options.X_STOP_AT_FACTS.value && options.X_SERVER_LOGIC.value) {
//            cpp.includeAtEnd("$analysis", "${Doop.souffleAddonsPath}/server-logic/queries.dl")
//        }
//
//        if (options.EXTRA_LOGIC.value) {
//            File extraLogic = new File(options.EXTRA_LOGIC.value as String)
//            if (extraLogic.exists()) {
//                String extraLogicPath = extraLogic.canonicalPath
//                log.info "Adding extra logic file ${extraLogicPath}"
//                cpp.includeAtEnd("${analysis}", extraLogicPath)
//            } else {
//                log.warn "Extra logic file does not exist: ${extraLogic}"
//            }
//        }
    }

    @Override
    void produceStats(File analysis) {
        def statsPath = "${Doop.soufflePythonPath}/addons/statistics"

        if (options.X_STATS_NONE.value) return

        cpp.includeAtEnd("$analysis", "${statsPath}/statistics-simple.dl")
//        if (options.X_STATS_FULL.value || options.X_STATS_DEFAULT.value) {
//            cpp.includeAtEnd("$analysis", "${statsPath}/statistics.dl")
//        }
    }

    @Override
    void processRelation(String query, Closure outputLineProcessor) {
        query = query.replaceAll(":", "_")
        def file = new File(this.outDir, "database/${query}.csv")
        if (!file.exists()) throw new FileNotFoundException(file.canonicalPath)
        file.eachLine { outputLineProcessor.call(it.replaceAll("\t", ", ")) }
    }
}
