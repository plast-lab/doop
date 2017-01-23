package org.clyze.doop

import groovy.transform.CompileStatic
import java.util.concurrent.*
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.clyze.analysis.Analysis
import org.clyze.doop.core.*
import org.clyze.doop.system.FileOps

/**
 * The entry point for the standalone doop app.
 */
@CompileStatic
class Main {

    private static final Log logger = LogFactory.getLog(Main)
    private static final int DEFAULT_TIMEOUT = 180 // 3 hours

    // Allow access to the analysis object from external code
    static Analysis analysis

    static void main(String[] args) {

        Doop.initDoop(System.getenv("DOOP_HOME"), System.getenv("DOOP_OUT"), System.getenv("DOOP_CACHE"))
        Helper.initLogging("INFO", "${Doop.doopHome}/build/logs", true)

        try {

            // The builder for displaying usage should not include non-standard flags
            CliBuilder usageBuilder = CommandLineAnalysisFactory.createCliBuilder(false)
            // The builder for displaying usage of non-standard flags
            CliBuilder nonStandardUsageBuilder = CommandLineAnalysisFactory.createNonStandardCliBuilder()
            // The builder for actually parsing the arguments needs to include non-standard flags
            CliBuilder builder = CommandLineAnalysisFactory.createCliBuilder(true)

            if (!args) {
                usageBuilder.usage()
                return
            }

            def argsToParse
            def bloxOptions
            def index = args.findIndexOf { arg -> arg == "--" }
            if (index == -1) {
                argsToParse = args
                bloxOptions = null
            }
            else {
                argsToParse = args[0..(index-1)]
                bloxOptions = args[(index+1)..(args.length-1)].join(' ')
            }

            OptionAccessor cli = builder.parse(argsToParse)

            if (!cli) {
                usageBuilder.usage()
                return
            }
            else if (cli.arguments().size() != 0) {
                println "Invalid argument specified: " + cli.arguments()[0]
                usageBuilder.usage()
                return
            }
            else if (cli['h']) {
                usageBuilder.usage()
                return
            }
            else if (cli['X']) {
                nonStandardUsageBuilder.usage()
                return
            }

            String userTimeout
            analysis
            if (cli['p']) {
                //create analysis from the properties file & the cli options
                String file = cli['p'] as String
                File f = FileOps.findFileOrThrow(file, "Not a valid file: $file")
                File propsBaseDir = f.getAbsoluteFile().getParentFile()
                Properties props = FileOps.loadProperties(f)

                changeLogLevel(cli['l'] ?: props.getProperty("level"))

                userTimeout = cli['t'] ?: props.getProperty("timeout")

                analysis = new CommandLineAnalysisFactory().newAnalysis(propsBaseDir, props, cli)
            }
            else {
                //create analysis from the cli options
                try {
                    Helper.checkMandatoryArgs(cli)
                }
                catch(e) {
                    println e.getMessage()
                    usageBuilder.usage()
                    return
                }

                changeLogLevel(cli['l'])

                userTimeout = cli['t']

                analysis = new CommandLineAnalysisFactory().newAnalysis(cli)
            }

            int timeout = Helper.parseTimeout(userTimeout, DEFAULT_TIMEOUT)
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            try {
                executorService.submit(new Runnable() {
                    @Override
                    void run() {
                        logger.info "Starting ${analysis.name} analysis on ${analysis.inputFiles[0]} - id: $analysis.id"
                        logger.debug analysis
                        analysis.options.BLOX_OPTS.value = bloxOptions
                        analysis.run()
                        new CommandLineAnalysisPostProcessor().process(analysis)
                    }
                }).get(timeout, TimeUnit.MINUTES)
            }
            catch (TimeoutException te) {
                logger.error "Timeout has expired ($timeout min)."
                executorService.shutdownNow()
                System.exit(-1)
            }
            executorService.shutdownNow()

        } catch (e) {
            if (logger.debugEnabled)
                logger.error(e.getMessage(), e)
            else
                logger.error(e.getMessage())
            System.exit(-1)
        }
    }

    private static void changeLogLevel(def logLevel) {
        if (logLevel) {
            switch (logLevel) {
                case "debug":
                    Logger.getRootLogger().setLevel(Level.DEBUG)
                    break
                case "info":
                    Logger.getRootLogger().setLevel(Level.INFO)
                    break
                case "error":
                    Logger.getRootLogger().setLevel(Level.ERROR)
                    break
                default:
                    logger.info "Invalid log level: $logLevel - using default (info)"
            }
        }
    }
}
