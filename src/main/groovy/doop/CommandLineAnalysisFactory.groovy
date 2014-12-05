package doop

import org.apache.commons.cli.Option

/**
 * A factory for creating Analysis objects from the command line.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 2/10/2014
 */
class CommandLineAnalysisFactory extends AnalysisFactory {

    /**
     * Processes the cli args and generates a new analysis.
     *
     * Note: To get the value of a cl option as a List, we need to append an s to its short name
     * (e.g., the short name of the DYNAMIC option is d, so we invoke ds). Obscure Cli builder feature.
     */
    Analysis newAnalysis(OptionAccessor cli) {

        //Get the name of the analysis (short option: a)
        String name = cli.a

        //Get the jars of the analysis (short option: j)
        List<String> jars = cli.js

        Map<String, AnalysisOption> options = Doop.createAnalysisOptions()
        options.findAll { Map.Entry<String, AnalysisOption> entry ->
            entry.value.cli //get the cli options
        }.each { Map.Entry<String, AnalysisOption> entry ->
            AnalysisOption option = entry.value
            String cliOptionName = option.name
            def optionValue = cli[(cliOptionName)]
            if (optionValue) {
                if (option.id == "DYNAMIC") {
                    //Obscure cli builder feature: to get the value of a cl option as a List, you need to append an s
                    //to its short name (the short name of the DYNAMIC option is d, so we invoke ds)
                    option.value = cli.ds
                }
                else if (option.argName) { //Only true-ish values are of interest (false or null values are ignored)
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
        return newAnalysis(name, jars, options)
    }

    /**
     * Creates the cli args from the respective analysis options (the ones with their cli property set to true).
     * This method provides special handling for the DYNAMIC option, in order to support multiple values for it.
     */
    static CliBuilder createCliBuilder() {

        List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option ->
            option.cli //all options with cli property
        }

        String usageHeader = "jdoop [OPTION]... ANALYSIS JAR\nAvailable analyses:\n\nAvailable options:\n"

        CliBuilder cli = new CliBuilder(
            usage:  "jdoop [OPTION]...",
        )
        cli.width = 120

        cli.with {
            h(longOpt: 'help', 'Display help and exit')
            r(longOpt: 'remote', 'Perform actions on the specified remote doop server', args:1, argName:'url')
            l(longOpt: 'level', 'Set the log level: debug, info or error (default: debug)', args:1, argName: 'loglevel')
            //p(longOpt: 'properties', 'Load doop properties file', args:1, argName: 'properties file')
            a(longOpt: 'analysis', "The name of the analysis: ${Helper.namesOfAvailableAnalyses(Doop.doopLogic).join(', ')}",
              args:1, argName:"name", required:true)
            j(longOpt: 'jar', "The jar files to analyze. Separate multiple jars with a comma", args:Option.UNLIMITED_VALUES, argName: "jar",
              valueSeparator: ",", required:true)

            cliOptions.each { AnalysisOption option ->
                if (option.id == "DYNAMIC") {
                    //Special handling of DYNAMIC option
                    d(longOpt: option.name, option.description, args: Option.UNLIMITED_VALUES, argName:option.argName,
                      valueSeparator:',' as char)
                }
                else if (option.argName) {
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
