package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.input.InputResolutionContext

@CompileStatic
@TypeChecked
@Deprecated
class SoundMayAnalysis extends ClassicAnalysis {

    protected SoundMayAnalysis(String id,
                               String name,
                               Map<String, AnalysisOption> options,
                               InputResolutionContext ctx,
                               File outDir,
                               File cacheDir,
                               List<File> inputFiles,
                               List<File> libraryFiles,
                               List<File> heapdlFiles,
                               List<File> platformLibs,
                               Map<String, String> commandsEnvironment) {
        super(id, name, options, ctx, outDir, cacheDir, inputFiles, libraryFiles, heapdlFiles, platformLibs, commandsEnvironment)
    }

    @Override
    void run() {
        //Initialize the instance here and not in the constructor, in order to allow an analysis to be re-runnable.
//        connector = new LBWorkspaceConnector(outDir,
//                                             options.BLOXBATCH.value as String,
//                                             (options.BLOX_OPTS.value ?: '') as String,
//                                             executor, cpp)
//
//        generateFacts()
//        if (options.X_STOP_AT_FACTS.value) return
//
//        initDatabase()
//        if (!options.X_STOP_AT_INIT.value) {
//
//            basicAnalysis()
//            if (!options.X_STOP_AT_BASIC.value) {
//
//                mainAnalysis()
//
//                produceStats()
//            }
//        }
//
//        logger.info "\nAnalysis START"
//        long t = Helper.timing { connector.processQueue() }
//        logger.info "Analysis END\n"
//        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
//        connector
//            .connect(database.toString())
//            .addBlock("""Stats:Runtime("script wall-clock time (sec)", $t).
//                         Stats:Runtime("disk footprint (KB)", $dbSize).""")
    }

    @Override
    protected void mainAnalysis() {
//        def analysisPath = "${Doop.analysesPath}/${name}"
//        def outFile = "${outDir}/sound.logic"
//        cpp.preprocess("${outDir}/string-constants.logic", "${Doop.logicPath}/main/string-constants.logic")
//
//        connector.queue()
//            .timedTransaction("-- String Constants --")
//            .addBlockFile("${outDir}/string-constants.logic")
//            .commit()
//            .elapsedTime()
//            .echo("-- Sound May Pointer Analysis --")
//
//        cpp
//            .enableLineMarkers()
//            .preprocess(outFile, "${analysisPath}/analysis.logic")
//            .disableLineMarkers()
//        Compiler.compileToLB(outFile, outDir).each { result ->
//            if (result.kind == Result.Kind.LOGIC)
//                connector.queue()
//                    .startTimer()
//                    .transaction()
//                    .addBlockFile(result.file.name)
//                    .commit()
//                    .elapsedTime()
//        }
    }
}
