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
        Helper.initLogging("INFO", "${Doop.doopHome}/logs", true)

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

            //The analysis is the first argument
            String name = arguments[0]
            //The jars are the following arguments
            List<String> jars = arguments.drop(1)

            Map<String, AnalysisOption> options = createAnalysisOptions(cli)
            Analysis analysis = new AnalysisFactory().newAnalysis(name, jars, options)
            logger.info "Starting $name analysis on ${jars[0]}"
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

        List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option -> option.definedByUser }

        CliBuilder cli = new CliBuilder(usage:'doop-java [OPTION]... ANALYSIS JAR')
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

    /**
     * Processes the cli args and generates a map of analysis options.
     */
    private static Map<String, AnalysisOption> createAnalysisOptions(OptionAccessor cli) {
        Map<String, AnalysisOption> options = Doop.createDefaultAnalysisOptions()
        options.findAll { Map.Entry<String, AnalysisOption> entry ->
            entry.value.definedByUser //get the cli options
        }.each { Map.Entry<String, AnalysisOption> entry ->
            AnalysisOption option = entry.value
            String cliOptionName = option.name
            def optionValue = cli[(cliOptionName)]
            if (optionValue) {
                //Only true-ish values are of interest (false or null values are ignored)
                if (option.argName) {
                    //if the cl option has an arg, the value of this arg defines the value of the respective
                    // analysis option
                    option.value = optionValue
                }
                else {
                    //the cl option has no arg and thus it is a boolean flag, toggling the default value of
                    // the respective analysis option
                    option.value = !option.value
                }
            }

        }
        return options
    }
}
