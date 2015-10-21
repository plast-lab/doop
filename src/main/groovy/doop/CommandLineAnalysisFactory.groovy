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

    static final String LOGLEVEL         = 'Set the log level: debug, info or error (default: info).'
    static final String ANALYSIS         = 'The name of the analysis.'
    static final String JAR              = 'The jar files to analyze. Separate multiple jars with a space. ' +
                                           ' If the argument is a directory, all its *.jar files will be included.'
    static final String PROPS            = 'The path to a properties file containing analysis options. This ' +
                                           'option: (a) can be mixed with any other option (except the analysis name ' +
                                           'and jars), (b) is processed prior to any others.'
    static final String TIMEOUT          = 'The analysis execution timeout in minutes (default: 180 - 3 hours).'
    static final String USER_SUPPLIED_ID = "The id of the analysis (if not specified, the id will be created " +
                                           "automatically). Permitted characters include letters, digits, " +
                                           "${EXTRA_ID_CHARACTERS.collect{"'$it'"}.join(', ')}."

    /**
     * Processes the cli args and generates a new analysis.
     */
    Analysis newAnalysis(OptionAccessor cli) {

        //Get the name of the analysis (short option: a)
        String name = cli.a

        //Get the jars of the analysis (short option: j)
        List<String> jars = cli.js

        //Get the id of the analysis (short option: id)
        String id = cli.id ?: null

        Map<String, AnalysisOption> options = Doop.overrideDefaultOptionsWithCLI(cli) { AnalysisOption option ->
            option.cli
        }
        return newAnalysis(id, name, options, jars)
    }

    /**
     * Processes the properties and the cli and generates a new analysis.
     */
    Analysis newAnalysis(File propsBaseDir, Properties props, OptionAccessor cli) {

        //Get the name of the analysis
        String name = props.getProperty("analysis")

        //Get the jars of the analysis
        List<String> jars = props.getProperty("jar").split().collect { String s-> s.trim() }
        //The jars, if relative, are being resolved via the propsBaseDir
        jars = jars.collect { String jar ->
            File f = new File(jar)
            return f.isAbsolute() ? jar : new File(propsBaseDir, jar).getCanonicalFile().getAbsolutePath()
        }

        //Get the optional id of the analysis
        String id = cli.id ?: props.getProperty("id")

        Map<String, AnalysisOption> options = Doop.overrideDefaultOptionsWithProperties(props) { AnalysisOption option ->
            option.cli
        }
        Doop.overrideOptionsWithCLI(options, cli) { AnalysisOption option -> option.cli }
        return newAnalysis(id, name, options, jars)
    }

    /**
     * Creates the cli args from the respective analysis options (the ones with their cli property set to true).
     * This method provides special handling for the DYNAMIC option, in order to support multiple values for it.
     */
    static CliBuilder createCliBuilder() {

        List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option ->
            option.cli //all options with cli property
        }

        def list = Helper.namesOfAvailableAnalyses("${Doop.doopLogic}/analyses").sort().join(', ')

        CliBuilder cli = new CliBuilder(
            parser: new org.apache.commons.cli.GnuParser (),
            usage:  "doop [OPTION]... -- [BLOXBATCH OPTION]...",
            footer: "Common Bloxbatch options:\n" +
                "-logicProfile N: Profile the execution of logic, show the top N predicates.\n" +
                "-logLevel LEVEL: Log the execution of logic at level LEVEL (for example: all).",
        )
        cli.width = 120

        cli.with {
            h(longOpt: 'help', 'Display help and exit.')
            l(longOpt: 'level', LOGLEVEL, args:1, argName: 'loglevel')
            a(longOpt: 'analysis', "$ANALYSIS Allowed values: $list.", args:1, argName:"name")
            id(longOpt:'identifier', USER_SUPPLIED_ID, args:1, argName: 'identifier')
            j(longOpt: 'jar', JAR, args:Option.UNLIMITED_VALUES, argName: "jar")
            p(longOpt: 'properties', PROPS, args:1, argName: "properties")
            t(longOpt: 'timeout', TIMEOUT, args:1, argName: 'timeout')
        }

        Helper.addAnalysisOptionsToCliBuilder(cliOptions, cli)

        return cli
    }

    /**
     * Creates the default properties file containing all the CLI-supported analysis options with empty values.
     * The file also contains the analysis id, name and jars as well as the log level and timeout.
     */
    static void createEmptyProperties(File f) {

        f.withWriter { Writer w ->

            w.write """\
                    #
                    #This is the skeleton of a doop properties file.
                    #Notes:
                    #- all file paths, if not absolute, should be relative to the directory that contains this file.
                    #- all booleans are processed using the java.lang.Boolean.parseBoolean() conventions.
                    #- all empty properties are ignored.
                    #

                    #
                    #analysis (string)
                    #$ANALYSIS
                    #
                    analysis =

                    #
                    #id (string)
                    #$USER_SUPPLIED_ID
                    #
                    id =

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

                    #
                    #timeout (number)
                    #$TIMEOUT
                    #
                    timeout =

                    """.stripIndent()

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
