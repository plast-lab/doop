package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.datalog.*
import org.clyze.doop.system.*

@CompileStatic
@TypeChecked
class SoundMayAnalysis extends ClassicAnalysis {

    protected SoundMayAnalysis(String id,
                       String outDirPath,
                       String cacheDirPath,
                       String name,
                       Map<String, AnalysisOption> options,
                       InputResolutionContext ctx,
                       List<File> inputs,
                       List<File> platformLibs,
                       Map<String, String> commandsEnvironment) {
        super(id, outDirPath, cacheDirPath, name, options, ctx, inputs, platformLibs, commandsEnvironment)
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
}
