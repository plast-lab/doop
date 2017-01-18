package org.clyze.doop.core

import groovy.transform.TypeChecked
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.common.*
import org.clyze.doop.datalog.*
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.system.*

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 */
@TypeChecked
abstract class DoopAnalysis extends Analysis implements Runnable {

    /**
     * Used for logging various messages
     */
    protected Log logger

    /**
     * The output dir for the analysis
     */
    File outDir

    /**
     * The facts dir for the input facts
     */
    protected File factsDir

    /**
     * The cache dir for the input facts
     */
    protected File cacheDir

    /**
     * The underlying workspace
     */
    protected File database

    /**
     * The dir used for running averroes
     */
    protected File averroesDir

    /**
     * The analysis input resolution mechanism
     */
    InputResolutionContext ctx

    /**
     * The jre library jars for soot
     */
    protected List<File> platformLibs

    /**
     * Used for invoking external commnands
     */
    protected Executor executor

    /**
     * Used for invoking the C preprocessor
     */
    protected CPreprocessor cpp

    /**
     * Interface with the underlying workspace
     */
    LBWorkspaceConnector connector

    /*
     * Use a java-way to construct the instance (instead of using Groovy's automatically generated Map constructor)
     * in order to ensure that internal state is initialized at one point and the init method is no longer required.
     */
    protected DoopAnalysis(String id,
                           String outDirPath,
                           String cacheDirPath,
                           String name,
                           Map<String, AnalysisOption> options,
                           InputResolutionContext ctx,
                           List<File> inputFiles,
                           List<File> platformLibs,
                           Map<String, String> commandsEnvironment) {
        super(AnalysisFamily.DOOP, id, name.replace(File.separator, "-"), options, inputFiles)
        this.ctx = ctx
        this.platformLibs = platformLibs

        logger      = LogFactory.getLog(getClass())

        outDir      = new File(outDirPath)
        cacheDir    = new File(cacheDirPath)
        factsDir    = new File(outDir, "facts")
        database    = new File(outDir, "database")
        averroesDir = new File(outDir, "averroes")

        executor    = new Executor(commandsEnvironment)
        cpp         = new CPreprocessor(this, executor)
    }

    @Override
    abstract public void run()

    abstract protected void generateFacts()

    abstract protected void initDatabase()

    abstract protected void basicAnalysis()

    abstract protected void mainAnalysis()

    abstract protected void produceStats()

    abstract protected void runSoot()

    abstract protected void runTransformInput()

    abstract protected void runJPhantom()

    abstract protected void runAverroes()



    public static long timing(Closure c) {
        long now = System.currentTimeMillis()
        try {
            c.call()
        }
        catch(e) {
            throw e
        }
        // We measure time only in error-free cases
        return ((System.currentTimeMillis() - now) / 1000).longValue()
    }
}
