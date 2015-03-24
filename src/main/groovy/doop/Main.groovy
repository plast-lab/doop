package doop

import doop.core.Analysis
import doop.core.Doop
import doop.core.Helper
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.util.concurrent.*

/**
 * The entry point for the standalone doop app.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 */
class Main {

    private static Log logger = LogFactory.getLog(Main)

    /**
     * The entry point.
     */
    static void main(String[] args) {

        String doopHome = System.getenv("DOOP_HOME")
        if (!doopHome) {
            println "DOOP_HOME environment variable is not set"
            System.exit(-1)
        }
        String doopOut = System.getenv("DOOP_OUT")

        Doop.initDoop(doopHome, doopOut)

        //initialize logging
        Helper.initLogging("INFO", "${Doop.doopHome}/logs", true)

        logger.debug "Command line options: $args"

        try {

            CliBuilder builder = CommandLineAnalysisFactory.createCliBuilder()
            OptionAccessor cli = builder.parse(args)

            if (!cli || !args || cli.h) {
                builder.usage()
                return
            }

            int timeout = 180 //3hours
            try {
                timeout = Integer.parseInt(cli.t)
            }
            catch(ex) {
                println "Using the default timeout ($timeout min)."
            }

            Analysis analysis
            if (cli.p) {
                //create analysis from the properties file
                String file = cli.p
                File f = Helper.checkFileOrThrowException(file, "Not a valid file: $file")

                Properties props = new Properties()
                f.withReader { Reader r -> props.load(r)}

                if (!checkProps(props)) {
                    return
                }

                //change the log level according to the property
                String logLevel = props.getProperty("level")
                changeLogLevel(logLevel)

                analysis = new CommandLineAnalysisFactory().newAnalysis(props)
            }
            else {
                //create analysis from the cli options
                if(!checkArgs(cli)) {
                    builder.usage()
                    return
                }

                //change the log level according to the cli arg
                def logLevel = cli.l
                changeLogLevel(logLevel)

                analysis = new CommandLineAnalysisFactory().newAnalysis(cli)
            }

            logger.info "Starting ${analysis.name} analysis on ${analysis.jars[0]} - id: $analysis.id"
            logger.debug analysis

            ExecutorService executor = Executors.newSingleThreadExecutor()
            Future future = executor.submit(new Runnable() {
                @Override
                void run() {
                    analysis.run()
                    analysis.printStats()
                    analysis.linkResult()
                }
            })

            try {
                future.get(timeout, TimeUnit.MINUTES)
            }
            catch (TimeoutException te) {
                logger.error("Timeout has expired ($timeout min).")
                System.exit(-1)
            }
            executor.shutdown()


        } catch (e) {
            if (logger.debugEnabled)
                logger.error(e.getMessage(), e)
            else
                logger.error(e.getMessage())
            System.exit(-1)
        }
    }

    private static boolean checkProps(Properties props) {
        boolean noAnalysis = !props.getProperty("analysis")?.trim()
        boolean noJar = !props.getProperty("jar")?.trim()
        boolean error = noAnalysis || noJar

        if (error)
            println "Missing required properties: " + (noAnalysis ? "analysis" : "") +
                    (noJar ? (noAnalysis ? ", " : "") + "jar" : "")

        return !error

    }

    private static boolean checkArgs(OptionAccessor cli) {
        boolean noAnalysis = !cli.a, noJar = !cli.j
        boolean error = noAnalysis || noJar

        if (error)
            println "Missing required argument(s): " + (noAnalysis ? "a" : "") +
                    (noJar ? (noAnalysis ? ", " : "") + "j" : "")

        return !error
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
