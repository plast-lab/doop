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
     * The entry point
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
        Helper.initLogging("DEBUG", "${Doop.doopHome}/logs", true)

        Log logger = LogFactory.getLog(Main)

        try {

            CliBuilder builder = createCliBuilder()
            OptionAccessor cli = builder.parse(args)
            if (!args || cli.h) {
                builder.usage()
                return
            }

            List<String> arguments = cli.arguments()
            if (!arguments || arguments.size() < 2) {
                builder.usage()
                return
            }

            //change the log level according to the cli arg
            String logLevel = cli.l
            if (logLevel == true) {
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
            logger.info "Starting ${analysis.name} analysis on ${analysis.jars[0]} - id: $analysis.id"
            logger.debug analysis

            analysis.run()

        } catch (e) {
            logger.error(e.getMessage(), e)
            System.exit(-1)
        }
    }


    /**
     * Creates the cli args from the respective analysis options (the ones with their definedByUser property set to true)
     */
    private static CliBuilder createCliBuilder() {

        List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option -> option.cli }

        String usageHeader = "jdoop [OPTION]... ANALYSIS JAR\nAvailable analyses:\n${Helper.availableAnalyses(Doop.doopLogic).join(' ')}\nAvailable options:\n"

        CliBuilder cli = new CliBuilder(
			usage:  "jdoop [OPTION]... ANALYSIS JAR", 
			header: "Available analyses:\n${Helper.availableAnalyses(Doop.doopLogic).join(' ')}"
		)
        cli.with {
            h(longOpt: 'help', 'Display help and exit')
            l(longOpt: 'level', 'Set the log level: debug, info or error (default: info)', args:1, argName: 'loglevel')
            //p(longOpt: 'properties', 'Load doop properties file', args:1, argName: 'properties file')

            cliOptions.each { AnalysisOption option ->
                if (option.argName) {
                    _(longOpt: option.name, option.description, args:1, argName:option.argName)
                }
                else {
                    _(longOpt: option.name, option.description)
                }
            }
        }

        return cli
    }
}
