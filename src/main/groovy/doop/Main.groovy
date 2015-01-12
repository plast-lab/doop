package doop

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger
/**
 * The entry point for the standalone doop app.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 */
class Main {

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

        Log logger = LogFactory.getLog(Main)

        logger.debug "Command line options: $args"

        try {

            CliBuilder builder = CommandLineAnalysisFactory.createCliBuilder()
            OptionAccessor cli = builder.parse(args)

            if (!cli) return

            if (!args || cli.h) {
                builder.usage()
                return
            }

            /*
            List<String> arguments = cli.arguments()
            logger.debug "Arguments: $arguments"
            if (!arguments || arguments.size() < 2) {
                logger.debug "No arguments"
                builder.usage()
                return
            }
            */

            //change the log level according to the cli arg
            def logLevel = cli.l
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

            Analysis analysis = new CommandLineAnalysisFactory().newAnalysis(cli)

            if (cli.r) {
                String url = cli.r
                logger.info "Posting ${analysis.name} analysis to remote server: $url"
                new Client(url).postNewAnalysis(analysis)

            }
            else {
                logger.info "Starting ${analysis.name} analysis on ${analysis.jars[0]} - id: $analysis.id"
                logger.debug analysis
                analysis.run()
                analysis.getStats()
                analysis.linkResult()
            }

        } catch (e) {
            logger.error(e.getMessage(), e)
            System.exit(-1)
        }
    }
}
