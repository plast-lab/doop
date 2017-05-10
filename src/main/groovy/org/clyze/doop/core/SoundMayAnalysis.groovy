package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.*
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.datalog.*
import org.clyze.doop.system.*
import org.clyze.deepdoop.system.*

@CompileStatic
@TypeChecked
class SoundMayAnalysis extends ClassicAnalysis {

    protected SoundMayAnalysis(String id,
                               String name,
                               Map<String, AnalysisOption> options,
                               InputResolutionContext ctx,
                               File outDir,
                               File cacheDir,
                               List<File> inputFiles,
                               List<File> platformLibs,
                               Map<String, String> commandsEnvironment) {
        super(id, name, options, ctx, outDir, cacheDir, inputFiles, platformLibs, commandsEnvironment)
    }

    @Override
    void run() {
        //Initialize the instance here and not in the constructor, in order to allow an analysis to be re-runnable.
        connector = new LBWorkspaceConnector(outDir,
                                             options.BLOXBATCH.value as String,
                                             (options.BLOX_OPTS.value ?: '') as String,
                                             executor, cpp)

        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return

        initDatabase()
        if (!options.X_STOP_AT_INIT.value) {

            basicAnalysis()
            if (!options.X_STOP_AT_BASIC.value) {

                mainAnalysis()

                produceStats()
            }
        }

        logger.info "\nAnalysis START"
        long t = timing { connector.processQueue() }
        logger.info "Analysis END\n"
        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
        connector
            .connect(database.toString())
            .addBlock("""Stats:Runtime("script wall-clock time (sec)", $t).
                         Stats:Runtime("disk footprint (KB)", $dbSize).""")
    }

    @Override
    protected void mainAnalysis() {
        def analysisPath = "${Doop.analysesPath}/${name}"
        def outFile = "${outDir}/sound.logic"
        cpp.preprocess("${outDir}/string-constants.logic", "${Doop.logicPath}/main/string-constants.logic")

        connector.queue()
            .timedTransaction("-- String Constants --")
            .addBlockFile("${outDir}/string-constants.logic")
            .commit()
            .elapsedTime()
            .echo("-- Sound May Pointer Analysis --")

        cpp
            .enableLineMarkers()
            .preprocess(outFile, "${analysisPath}/analysis.logic")
            .disableLineMarkers()
        Compiler.compile(outDir.toString(), outFile).each { result ->
            if (result.kind == Result.Kind.LOGIC)
                connector.queue()
                    .startTimer()
                    .transaction()
                    .addBlockFile(result.file.name)
                    .commit()
                    .elapsedTime()
        }
    }
}
