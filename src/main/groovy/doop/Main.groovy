package doop

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 */
class Main {

    static void main(String[] args) {

        //Bootstrapping errors bleed through
        Doop.bootstrap()

        Log logger = LogFactory.getLog(Main)

        try {

            CliBuilder builder = createCliProcessor()
            OptionAccessor cli = builder.parse(args)
            if (!args || cli.h) {
                builder.usage()
                return
            }

            List<String> arguments = cli.arguments()
            if (!arguments || arguments.size() != 2) { //TODO: One jar for now
                builder.usage()
                return
            }

            //The analysis is the first argument
            String name = arguments[0]
            //The jar is the second (only one for now)
            String jar = arguments[1]

            Map<String, AnalysisOption> options = createAnalysisOptions(builder, cli)
            options.JAR.value = jar
            Analysis analysis = Doop.newAnalysis(name, options)
            println "Processing analysis:"
            println analysis

            long now = System.currentTimeMillis()
            logger.debug("### Start of analysis: $name")

            //TODO: Run them in parallel
            analysis.preprocessLogic()
            analysis.dealWithPhantomRefs()

            String ms = "${System.currentTimeMillis() - now}ms"
            logger.debug("### End of analysis: $name ($ms)")

            println "Done ($ms)"

        } catch (e) {
            println "Error: ${e.getMessage()} (${e.getClass().getName()}). Check the log for details."
            logger.error(e.getMessage(), e)
            System.exit(-1)
        }
    }


    //Perhaps use a property file instead?
    private static CliBuilder createCliProcessor() {

        CliBuilder cli = new CliBuilder(usage:'doop-java [OPTION]... ANALYSIS JAR')
        //TODO: Generate the supported cli arguments from the analysis options
        cli.with {
            h(longOpt: 'help',             'Show help')
            m(longOpt: 'main',             'Specify the main class', args:1, argName:'mainClass')
            _(longOpt: 'allow-phantom',    'Allow non-existent referenced jars')
        }
        return cli

        /*
        CliBuilder cli = new CliBuilder(usage:'doop-java [OPTION]... ANALYSIS JAR [-- [BLOXOPTION]...')
        cli.with {
            h(longOpt: 'help',             'Show help')
            m(longOpt: 'main',             'Specify the main class')
            c(longOpt: 'cache',            'The analysis will use the cached input relations, if such exist')
            t(longOpt: 'transform-input' , 'Transform input by removing redundant instructions')
            j(longOpt: 'jre'             , 'One of 1.3, 1.4, 1.5, 1.6 (default: system)')
            i(longOpt: 'interactive'     , 'Enable interactive mode')
            _(longOpt: 'allow-phantom'   , 'Allow non-existent referenced jars')
            _(longOpt: 'solo-run'        , 'Perform checks to ensure no instance of bloxbatch is already running')
            _(longOpt: 'log-memory-stats', 'Log virtual memory statistics (currently Linux only, uses vmstat)') //OS-specific
            _(longOpt: 'full-stats'      , 'Load additional logic for collecting statistics')
            _(longOpt: 'sanity'          , 'Load additional logic for sanity checks')
            _(longOpt: 'averroes'        , 'Use averroes tool to create a placeholder library')
            _(longOpt: 'tamiflex'        , 'File with tamiflex data (multiple occurrences disallowed)', args:1, argName:'FILE')
            _(longOpt: 'dynamic'         , 'File with tab-separated data for Config:DynamicClass (multiple occurrences allowed)', args:1, argName:'FILE')
            _(longOpt: 'client'          , 'Additional directory/file of client analysis to include', args:1, argName:'PATH')
        }
        return cli
        */
    }

    private static Map<String, AnalysisOption> createAnalysisOptions(CliBuilder builder, OptionAccessor cli) {
        Map<String, AnalysisOption> options = Doop.createDefaultAnalysisOptions()
        options.findAll { Map.Entry<String, AnalysisOption> entry ->
            entry.value.cli
        }.each { Map.Entry<String, AnalysisOption> entry ->
            String cliOptionName = entry.value.cliName
            if (builder.options.getOption(cliOptionName).hasArgs()) {
                //if the cl option has args, the value of the arg defines the value of the respective analysis option
                entry.value.value =  cli[(cliOptionName)]
            }
            else {
                if (cli[cliOptionName]) {
                    //a boolean cl option toggles the default value of the respective analysis option
                    entry.value.value = !entry.value.value
                }
            }
        }
        return options
    }
}
