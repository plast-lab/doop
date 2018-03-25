package org.clyze.doop

import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis
import org.clyze.doop.soot.DoopErrorCodeException
import org.clyze.utils.FileOps
import org.clyze.utils.Helper

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The entry point for the standalone doop app.
 */
@CompileStatic
class Main {

    private static final Log logger = LogFactory.getLog(Main)
    private static final int DEFAULT_TIMEOUT = 90 // 90 minutes

    // Allow access to the analysis object from external code
    static DoopAnalysis analysis

    static void main(String[] args) {

        Doop.initDoop(System.getenv("DOOP_HOME"), System.getenv("DOOP_OUT"), System.getenv("DOOP_CACHE"))
        Helper.initLogging("INFO", "${Doop.doopHome}/build/logs", true)

        try {
            // The builder for displaying usage should not include non-standard flags
            def usageBuilder = CommandLineAnalysisFactory.createCliBuilder(false)
            // The builder for displaying usage of non-standard flags
            def nonStandardUsageBuilder = CommandLineAnalysisFactory.createNonStandardCliBuilder()
            // The builder for actually parsing the arguments needs to include non-standard flags
            def builder = CommandLineAnalysisFactory.createCliBuilder(true)

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
            } else {
                argsToParse = args[0..(index - 1)]
                bloxOptions = args[(index + 1)..(args.length - 1)].join(' ')
            }

            OptionAccessor cli = builder.parse(argsToParse)

            if (!cli) {
                usageBuilder.usage()
                return
            } else if (cli.arguments().size() != 0) {
                logger.info "Invalid argument specified: " + cli.arguments()[0]
                usageBuilder.usage()
                return
            } else if (cli['h']) {
                usageBuilder.usage()
                return
            } else if (cli['X']) {
                nonStandardUsageBuilder.usage()
                return
            }

            String userTimeout
            try {
                if (cli['p']) {
                    //create analysis from the properties file & the cli options
                    String file = cli['p'] as String
                    File f = FileOps.findFileOrThrow(file, "Not a valid file: $file")
                    File propsBaseDir = f.getAbsoluteFile().getParentFile()
                    Properties props = FileOps.loadProperties(f)

                    changeLogLevel(cli['L'] ?: props.getProperty("level"))

                    userTimeout = cli['t'] ?: props.getProperty("timeout")

                    analysis = new CommandLineAnalysisFactory().newAnalysis(propsBaseDir, props, cli)
                } else {
                    changeLogLevel(cli['L'])

                    userTimeout = cli['t']

                    analysis = new CommandLineAnalysisFactory().newAnalysis(cli)
                }
            }
            catch (e) {
                println e.getMessage()
                usageBuilder.usage()
                return
            }

            int timeout = parseTimeout(userTimeout, DEFAULT_TIMEOUT)
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            try {
                executorService.submit(new Runnable() {
                    @Override
                    void run() {
                        if (!analysis.options.X_START_AFTER_FACTS.value) {
                            logger.info "Starting ${analysis.name} analysis"
                            logger.info "Id       : $analysis.id"
                            logger.info "Inputs   : ${analysis.inputFiles.join(', ')}"
                            logger.info "Libraries: ${analysis.libraryFiles.join(', ')}"
                        }
                        else
                            logger.info "Starting ${analysis.name} analysis on user-provided facts at ${analysis.options.X_START_AFTER_FACTS.value} - id: $analysis.id"
                        logger.debug analysis
                        analysis.options.BLOX_OPTS.value = bloxOptions
                        try {
                            analysis.run()
                        } catch (DoopErrorCodeException ex) {
                            // Don't continue with the analysis.
                            return
                        }
                        new CommandLineAnalysisPostProcessor().process(analysis)
                    }
                }).get(timeout, TimeUnit.MINUTES)
            }
            catch (TimeoutException te) {
                logger.error "Timeout has expired ($timeout min)."
            } finally {
                executorService.shutdownNow()
            }
        } catch (e) {
            e = (e.getCause() ?: e)
            logger.error(e.getMessage(), e)
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

    private static int parseTimeout(String userTimeout, int defaultTimeout) {
        int timeout = defaultTimeout
        try {
            timeout = Integer.parseInt(userTimeout)
        }
        catch (ex) {
            logger.info "Using the default timeout ($timeout min)."
            return defaultTimeout
        }

        if (timeout <= 0) {
            logger.info "Invalid user supplied timeout: $timeout - using the default ($defaultTimeout min)."
            return defaultTimeout
        } else {
            logger.info "Using a timeout of $timeout min."
            return timeout
        }
    }
}
