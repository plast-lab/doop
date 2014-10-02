package doop

/**
 * A factory for creating Analysis objects from the command line.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 2/10/2014
 */
class CommandLineAnalysisFactory extends AnalysisFactory {

    /**
     * Processes the cli args and generates a new analysis.
     * TODO: support multiple values for the DYNAMIC option
     */
    Analysis newAnalysis(OptionAccessor cli) {
        List<String> arguments = cli.arguments()

        //The analysis is the first argument
        String name = arguments[0]

        //The jars are the following arguments
        List<String> jars = arguments.drop(1)

        Map<String, AnalysisOption> options = Doop.createDefaultAnalysisOptions()
        options.findAll { Map.Entry<String, AnalysisOption> entry ->
            entry.value.cli //get the cli options
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
        return newAnalysis(name, jars, options)
    }
}
