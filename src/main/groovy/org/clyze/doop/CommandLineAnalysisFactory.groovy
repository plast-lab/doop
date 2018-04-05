package org.clyze.doop

import org.apache.commons.cli.Option
import org.clyze.analysis.AnalysisFamily
import org.clyze.analysis.AnalysisOption
import org.clyze.analysis.BooleanAnalysisOption
import org.clyze.analysis.IntegerAnalysisOption
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis
import org.clyze.doop.core.DoopAnalysisFactory
import org.clyze.doop.core.DoopAnalysisFamily

/**
 * A factory for creating Analysis objects from the command line.
 */
class CommandLineAnalysisFactory extends DoopAnalysisFactory {

    static final String LOGLEVEL         = 'Set the log level: debug, info or error (default: info).'
    static final String ANALYSIS         = 'The name of the analysis.'
    static final String INPUTS           = 'The input (application) files to analyze. Separate multiple files with a space. ' +
                                           'If the argument is a directory, all its *.jar files will be included.'
    static final String LIBRARIES        = 'The library files to use for dependency resolution. Separate multiple files with a space. ' +
                                           'If the argument is a directory, all its *.jar files will be included.'
    static final String PROPS            = 'The path to a properties file containing analysis options. This ' +
                                           'option can be mixed with any other and is processed first.'
    static final String TIMEOUT          = 'The analysis execution timeout in minutes (default: 90 minutes).'
    static final String USER_SUPPLIED_ID = "The id of the analysis (if not specified, the id will be created " +
                                           "automatically). Permitted characters include letters, digits, " +
                                           "${EXTRA_ID_CHARACTERS.collect{"'$it'"}.join(', ')}."
    static final String USAGE            = "doop [OPTION]... -- [BLOXBATCH OPTION]..."
    static final int    WIDTH            = 120

    static final AnalysisFamily FAMILY   = DoopAnalysisFamily.instance

    /**
     * Processes the cli args and generates a new analysis.
     */
    DoopAnalysis newAnalysis(OptionAccessor cli) {

        //Get the name of the analysis (short option: a)
        String name = cli.a

        //Get the id of the analysis (short option: id)
        String id = cli.id ?: null

        Map<String, AnalysisOption> options = Doop.overrideDefaultOptionsWithCLI(cli) { AnalysisOption option ->
            option.cli
        }

        //Get the inputFiles of the analysis (short option: i)
        //Get the libraryFiles of the analysis (short option: l)
        List<String> inputs = (!options.X_START_AFTER_FACTS.value && cli.is) ? cli.is : []
        List<String> libraries = (!options.X_START_AFTER_FACTS.value && cli.ls) ? cli.ls : []

        return newAnalysis(FAMILY, id, name, options, inputs, libraries)
    }

    /**
     * Processes the properties and the cli and generates a new analysis.
     */
    DoopAnalysis newAnalysis(File propsBaseDir, Properties props, OptionAccessor cli) {

        //Get the name of the analysis
        String name = cli.a ?: props.getProperty("analysis")

        //Get the inputFiles of the analysis. If there are no inputFiles in the CLI, we get them from the properties.
        List<String> inputs
        List<String> libraries
        if (!cli.is) {
            inputs = props.getProperty("inputFiles").split().collect { String s -> s.trim() }
            // The inputFiles, if relative, are being resolved via the propsBaseDir or later if they are URLs
            inputs = inputs.collect { String input ->
                try {
                    // If it is not a valid URL an exception is thrown
                    URL url = new URL(input)
                    return input
                }
                catch (e) {}
                File f = new File(input)
                return f.isAbsolute() ? input : new File(propsBaseDir, input).getCanonicalFile().getAbsolutePath()
            }
        }
        else
            inputs = cli.is

        if (!cli.ls) {
            libraries = props.getProperty("libraryFiles").split().collect { String s -> s.trim() }
            // The inputFiles, if relative, are being resolved via the propsBaseDir or later if they are URLs
            libraries = libraries.collect { String lib ->
                try {
                    // If it is not a valid URL an exception is thrown
                    URL url = new URL(lib)
                    return lib
                }
                catch (e) {}
                File f = new File(lib)
                return f.isAbsolute() ? lib : new File(propsBaseDir, lib).getCanonicalFile().getAbsolutePath()
            }
        }
        else
            libraries = cli.ls

        //Get the optional id of the analysis
        String id = cli.id ?: props.getProperty("id")

        Map<String, AnalysisOption> options = Doop.overrideDefaultOptionsWithPropertiesAndCLI(props, cli) { AnalysisOption option ->
            option.cli
        }
        return newAnalysis(FAMILY, id, name, options, inputs, libraries)
    }

    /**
     * Creates the cli args from the respective analysis options (the ones with their cli property set to true).
     * This method provides special handling for the DYNAMIC option, in order to support multiple values for it.
     */
    static CliBuilder createCliBuilder(boolean includeNonStandard) {

        List<AnalysisOption> cliOptions = FAMILY.supportedOptions().findAll { AnalysisOption option ->
            option.cli && (includeNonStandard || !option.nonStandard) //all options with cli property
        }

        CliBuilder cli = new CliBuilder(
            parser: new org.apache.commons.cli.GnuParser (),
            usage:  USAGE,
            footer: "\nCommon Bloxbatch options:\n" +
                "-logicProfile N: Profile the execution of logic, show the top N predicates.\n" +
                "-logLevel LEVEL: Log the execution of logic at level LEVEL (for example: all).",
            width:  WIDTH,
        )

        cli.with {
            h(longOpt: 'help', 'Display help and exit.')
            L(longOpt: 'Level', LOGLEVEL, args:1, argName: 'LOG_LEVEL')
            p(longOpt: 'properties', PROPS, args:1, argName: "properties")
            t(longOpt: 'timeout', TIMEOUT, args:1, argName: 'TIMEOUT')
            X(longOpt: 'X', 'Display information about non-standard options and exit.')
        }

        addAnalysisOptionsToCliBuilder(cliOptions, cli)

        return cli
    }

    /**
     * Creates the nonStandard args from the respective analysis options (the ones with their nonStandard property set to true).
     */
    static CliBuilder createNonStandardCliBuilder() {

        List<AnalysisOption> cliOptions = FAMILY.supportedOptions().findAll { AnalysisOption option ->
            option.nonStandard //all options with nonStandard property
        }

        CliBuilder cli = new CliBuilder(
            parser: new org.apache.commons.cli.GnuParser (),
            usage:  USAGE,
            footer: "\nThese options are non-standard and subject to change without notice.",
            width:  WIDTH,
        )

        addAnalysisOptionsToCliBuilder(cliOptions, cli)

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
                    #inputFiles (file(s))
                    #$INPUTS
                    #
                    inputFiles =

#
                    #libraryFiles (file(s))
                    #$LIBRARIES
                    #
                    libraryFiles =

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
            List<AnalysisOption> cliOptions = FAMILY.supportedOptions().findAll { AnalysisOption option ->
                option.cli
            }.sort{ AnalysisOption option ->
                option.id
            }

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
        else if (option.argName && option instanceof BooleanAnalysisOption) {
            type = "(boolean)"
        }
        else if (option.argName && option instanceof IntegerAnalysisOption) {
            type = "(boolean)"
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

    /**
     * Adds the list of analysis options to the cli builder.
     * @param options - the list of AnalysisOption items to add.
     * @param cli - the cli builder.
     */
    private static void addAnalysisOptionsToCliBuilder(List<AnalysisOption> options, CliBuilder cli) {
        convertAnalysisOptionsToCliOptions(options).each { cli << it }
    }

    private static String desc(AnalysisOption option) {
        option.validValues ? "$option.description Valid values: ${option.validValues.join(", ")}" : option.description
    }

    static List<Option> convertAnalysisOptionsToCliOptions(List<AnalysisOption> options) {
        options.collect { AnalysisOption option ->
            if (option.id == "ANALYSIS") {
                Option o = new Option('a', option.name, true, desc(option))
                o.setArgName(option.argName)
                return o
            } else if (option.id == "INPUTS") {
                Option o = new Option('i', option.name, true, option.description)
                o.setArgs(Option.UNLIMITED_VALUES)
                o.setArgName(option.argName)
                return o
            } else if (option.id == "LIBRARIES") {
                Option o = new Option('l', option.name, true, option.description)
                o.setArgs(Option.UNLIMITED_VALUES)
                o.setArgName(option.argName)
                return o
            } else if (option.id == "DYNAMIC") {
                Option o = new Option('d', option.name, true, option.description)
                o.setArgs(Option.UNLIMITED_VALUES)
                o.setArgName(option.argName)
                return o
            } else if (option.id == "HEAPDL") {
                Option o = new Option(null, option.name, true, option.description)
                o.setArgs(Option.UNLIMITED_VALUES)
                o.setArgName(option.argName)
                return o
            } else if (option.argName) {
                //Option accepts a String value
                Option o = new Option(null, option.name, true, desc(option))
                o.setArgName(option.argName)
                return o
            } else {
                //Option is a boolean
                return new Option(null, option.name, false, option.description)
            }
        }
    }
}
