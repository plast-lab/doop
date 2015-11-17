package doop

import doop.core.Analysis
import doop.core.AnalysisOption
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
    private static final int DEFAULT_TIMEOUT = 180 //3 hours

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

        try {

            CliBuilder builder = CommandLineAnalysisFactory.createCliBuilder()

            if(!args) {
                builder.usage()
                return
            }

            //Check for bloxbath options
            int len = args.length
            int index = -1
            int i = 0
            while(i<len) {
                if (args[i] == "--") {
                    index = i
                    break
                }
                i++
            }

            def argsToParse
            String bloxOptions

            if (index == -1) {
                //no bloxbatch options
                argsToParse = args
                bloxOptions = null
            }
            else {
                argsToParse = args[0..index-1]
                bloxOptions = args[index+1..len-1].join(' ')
            }

            OptionAccessor cli = builder.parse(argsToParse)

            if (!cli || cli.h) {
                builder.usage()
                return
            }


            String userTimeout
            Analysis analysis
            if (cli.p) {
                //create analysis from the properties file & the cli options
                String file = cli.p
                File f = Helper.checkFileOrThrowException(file, "Not a valid file: $file")
                File propsBaseDir = f.getParentFile()
                Properties props = Helper.loadProperties(f)

                try {
                    Helper.checkMandatoryProps(props)
                }
                catch(e) {
                    println e.getMessage()
                    return
                }

                //change the log level according to the property or cli arg
                changeLogLevel(cli.l ?: props.getProperty("level"))

                //set the timeout according to the property or cli arg
                userTimeout = cli.t ?: props.getProperty("timeout")

                analysis = new CommandLineAnalysisFactory().newAnalysis(propsBaseDir, props, cli)
            }
            else {
                //create analysis from the cli options
                try {
                    Helper.checkMandatoryArgs(cli)
                }
                catch(e) {
                    println e.getMessage()
                    builder.usage()
                    return
                }

                //change the log level according to the cli arg
                changeLogLevel(cli.l)

                //set the timeout according to the cli arg
                userTimeout = cli.t

                analysis = new CommandLineAnalysisFactory().newAnalysis(cli)
            }

            analysis.options.BLOX_OPTS.value = bloxOptions

            logger.info "Starting ${analysis.name} analysis on ${analysis.jars[0]} - id: $analysis.id"
            logger.debug analysis

            int timeout = Helper.parseTimeout(userTimeout, DEFAULT_TIMEOUT)

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
