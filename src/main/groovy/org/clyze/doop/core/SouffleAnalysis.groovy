package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.clyze.doop.input.InputResolutionContext

@CompileStatic
@TypeChecked
class SouffleAnalysis extends Analysis {
    long sootTime

    protected SouffleAnalysis(String id,
                              String outDirPath,
                              String cacheDirPath,
                              String name,
                              Map<String, AnalysisOption> options,
                              InputResolutionContext ctx,
                              List<File> inputs,
                              List<File> platformLibs,
                              Map<String, String> commandsEnvironment) {
        super(id, outDirPath, cacheDirPath, name, options, ctx, inputs, platformLibs, commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }
    }

    String toString() {
        return [id:id, name:safename, outDir:outDir, cacheDir:cacheDir, inputs:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
                "\n" +
                options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    @Override
    void run() {

    }

    @Override
    protected void generateFacts() {

    }

    @Override
    protected void initDatabase() {

    }

    @Override
    protected void basicAnalysis() {

    }

    @Override
    protected void mainAnalysis() {

    }

    @Override
    protected void produceStats() {

    }

    @Override
    protected void runSoot() {

    }

    @Override
    protected void runTransformInput() {

    }

    @Override
    protected void runJPhantom() {

    }

    @Override
    protected void runAverroes() {

    }
}
