package doop

import doop.core.Analysis
import doop.core.AnalysisFactory
import doop.core.AnalysisOption
import doop.core.Doop
import doop.core.Helper
import org.apache.commons.cli.Option

/**
 * A factory for creating Analysis objects from the command line.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 2/10/2014
 */
class CommandLineAnalysisFactory extends AnalysisFactory {

    private static final String LOGLEVEL = 'Set the log level: debug, info or error (default: info).'
    private static final String ANALYSIS = 'The name of the analysis.'
    private static final String JAR      = 'The jar files to analyze. Separate multiple jars with a comma. If the ' +
                                           ' argument is a directory, all its *.jar files will be included.'
    private static final String PROPS    = 'The path to a properties file containing analysis options. If this ' +
                                           'option is given, all other options are ignored.'

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

        Map<String, AnalysisOption> options = Doop.createDefaultAnalysisOptions()

        options.findAll { Map.Entry<String, AnalysisOption> entry ->
            entry.value.cli //get the cli options
        }.each { Map.Entry<String, AnalysisOption> entry ->
            AnalysisOption option = entry.value
            String cliOptionName = option.name
            def optionValue = cli[(cliOptionName)]
            if (optionValue) { //Only true-ish values are of interest (false or null values are ignored)
                if (option.id == "DYNAMIC") {
                    //Obscure cli builder feature: to get the value of a cl option as a List, you need to append an s
                    //to its short name (the short name of the DYNAMIC option is d, so we invoke ds)
                    option.value = cli.ds
                } else if (option.argName) {
                    //if the cl option has an arg, the value of this arg defines the value of the respective
                    // analysis option
                    option.value = optionValue
                } else {
                    //the cl option has no arg and thus it is a boolean flag, toggling the default value of
                    // the respective analysis option
                    option.value = !option.value
                }
            }
        }
        return newAnalysis(name, jars, options)
    }

    /**
     * Processes the properties and generates a new analysis.
     */
    Analysis newAnalysis(Properties props) {

        //Get the name of the analysis
        String name = props.getProperty("analysis")

        //Get the jars of the analysis
        List<String> jars = props.getProperty("jar").split(",").collect { String s-> s.trim() }

        Map<String, AnalysisOption> options = Doop.createDefaultAnalysisOptions()

        Doop.overrideAnalysisOptionsFromProperties(options, props)

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

        def list = Helper.namesOfAvailableAnalyses(Doop.doopLogic).join(', ')

        CliBuilder cli = new CliBuilder(
            usage:  "doop [OPTION]...",
        )
        cli.width = 120

        cli.with {
            h(longOpt: 'help', 'Display help and exit.')
            l(longOpt: 'level', LOGLEVEL, args:1, argName: 'loglevel')
            a(longOpt: 'analysis', "$ANALYSIS Allowed values: $list.", args:1, argName:"name")
            j(longOpt: 'jar', JAR, args:Option.UNLIMITED_VALUES, argName: "jar", valueSeparator: ",")
            p(longOpt: 'properties', PROPS, args:1, argName: "properties")
        }

        Helper.addAnalysisOptionsToCliBuilder(cliOptions, cli)

        return cli
    }

    /**
     * Creates the default properties file containing all the supported analysis options with empty values.
     * The file also contains the analysis name, the jars and the log level.
     */
    static void createEmptyProperties(File f) {

        f.withWriter { Writer w ->

            w.write """#
#This is the skeleton of a doop properties file.
#Notes:
#- all file paths, if not absolute, should be given relative to the directory that
#  doop is invoked from (and not relative to the directory this file is located).
#- all booleans are processed using the java.lang.Boolean.parseBoolean() conventions.
#- all empty properties are ignored.
#

#
#analysis (string)
#$ANALYSIS
#
analysis =

#
#jar (file)
#$JAR
#
jar =

#
#level (string)
#$LOGLEVEL
#
level =

"""

            //Find all cli options and sort them by id
            List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option ->
                option.cli
            }.sort{ AnalysisOption option ->
                option.id
            }

            //Put the "main" options first
            cliOptions = cliOptions.findAll { AnalysisOption option -> !option.isAdvanced } +
                    cliOptions.findAll { AnalysisOption option -> option.isAdvanced }

            cliOptions.each { AnalysisOption option ->
                writeAsProperty(option, w)
            }
        }
    }

    /**
     * Writes the given analysis option to the given writer using the standard Java syntax for properties files.
     */
    private static void writeAsProperty(AnalysisOption option, Writer w) {
        def type, id = option.id.toLowerCase()

        if (option.isFile) {
            type = "(file)"
        }
        else if (option.argName) {
            type = "(string)"
        }
        else {
            type = "(boolean)"
        }

        w.write "#\n"
        w.write "#$id $type\n"
        if (option.description) w.write "#${option.description} \n"
        w.write "#\n"
        w.write "$id = \n\n"
    }
}
