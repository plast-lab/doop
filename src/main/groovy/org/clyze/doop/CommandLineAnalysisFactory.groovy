package org.clyze.doop

import org.apache.commons.cli.GnuParser
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

	static final String LOGLEVEL = 'Set the log level: debug, info or error (default: info).'
	static final String ANALYSIS = 'The name of the analysis.'
	static final String INPUTS = 'The input (application) files to analyze. Separate multiple files with a space. ' +
			'If the argument is a directory, all its *.jar files will be included.'
	static
	final String LIBRARIES = 'The library files to use for dependency resolution. Separate multiple files with a space. ' +
			'If the argument is a directory, all its *.jar files will be included.'
	static final String HEAPDL = 'The heap dumps to use with HeapDL. Separate multiple files with a space. '
	static final String PROPS = 'The path to a properties file containing analysis options. This ' +
			'option can be mixed with any other and is processed first.'
	static final String TIMEOUT = 'The analysis execution timeout in minutes (default: 90 minutes).'
	static final String USER_SUPPLIED_ID = "The id of the analysis (if not specified, the id will be created " +
			"automatically). Permitted characters include letters, digits, " +
			"${EXTRA_ID_CHARACTERS.collect { "'$it'" }.join(', ')}."
	static final String USAGE = "doop [OPTION]... -- [BLOXBATCH OPTION]..."
	static final int WIDTH = 120

	static final AnalysisFamily FAMILY = DoopAnalysisFamily.instance

	/**
	 * Processes the cli args and generates a new analysis.
	 */
	DoopAnalysis newAnalysis(OptionAccessor cli) {
		def options = Doop.overrideDefaultOptionsWithCLI(cli) { it.cli }
		// Get the id of the analysis (short option: id)
		options.USER_SUPPLIED_ID.value = cli.id ?: null
		// Get the name of the analysis (short option: a)
		options.ANALYSIS.value = cli.a
		// Get the inputFiles of the analysis (short option: i)
		options.INPUTS.value = (!options.X_START_AFTER_FACTS.value && cli.is) ? cli.is : []
		// Get the libraryFiles of the analysis (short option: l)
		options.LIBRARIES.value = (!options.X_START_AFTER_FACTS.value && cli.ls) ? cli.ls : []
		// Get the heapFiles of the analysis (long option: heapdl)

		newAnalysis(FAMILY, options)
	}

	/**
	 * Processes the properties and the cli and generates a new analysis.
	 */
	DoopAnalysis newAnalysis(File propsBaseDir, Properties props, OptionAccessor cli) {
		def options = Doop.overrideDefaultOptionsWithPropertiesAndCLI(props, cli) { it.cli }
		// Get the id of the analysis (short option: id)
		options.USER_SUPPLIED_ID.value = cli.id ?: props.getProperty("id")
		// Get the name of the analysis (short option: a)
		options.ANALYSIS.value = cli.a ?: props.getProperty("analysis")
		// Get the inputFiles of the analysis (short option: i)
		options.INPUTS.value = cli.is ?: getFromProperties(propsBaseDir, props, "inputFiles")
		// Get the libraryFiles of the analysis (short option: l)
		options.LIBRARIES.value = cli.ls ?: getFromProperties(propsBaseDir, props, "libraryFiles")
		// Get the heapFiles of the analysis (long option: heapdl)
		options.HEAPDLS.value = cli.heapdls ?: getFromProperties(propsBaseDir, props, "heapFiles")

		newAnalysis(FAMILY, options)
	}

	private static List<String> getFromProperties(File propsBaseDir, Properties props, String what) {
		// Files, if relative, are being resolved via the propsBaseDir or later if they are URLs
		return props.getProperty(what).split().collect {
			try {
				it.trim()
				// If it is not a valid URL an exception is thrown
				def url = new URL(it)
				return it
			} catch (e) {
			}
			def f = new File(it)
			return f.absolute ? it : new File(propsBaseDir, it).canonicalFile.absolutePath
		}
	}

	static CliBuilder createCliBuilder() {
		List<AnalysisOption> cliOptions = FAMILY.supportedOptions().findAll { it.cli }

		def cli = new CliBuilder(
				parser: new GnuParser(),
				usage: USAGE,
				footer: "\nCommon Bloxbatch options:\n" +
						"-logicProfile N: Profile the execution of logic, show the top N predicates.\n" +
						"-logLevel LEVEL: Log the execution of logic at level LEVEL (for example: all).",
				width: WIDTH,
		)

		cli.with {
			h(longOpt: 'help', 'Display help and exit.')
			L(longOpt: 'level', LOGLEVEL, args: 1, argName: 'LOG_LEVEL')
			p(longOpt: 'properties', PROPS, args: 1, argName: "PROPERTIES")
			t(longOpt: 'timeout', TIMEOUT, args: 1, argName: 'TIMEOUT')
		}

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

                    #heapFiles (file(s))
                    #$HEAPDL
                    #
                    heapFiles =

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
			FAMILY.supportedOptions().findAll { it.cli }.sort { it.id }.each { writeAsProperty(it, w) }
		}
	}

	/**
	 * Writes the given analysis option to the given writer using the standard Java syntax for properties files.
	 */
	private static void writeAsProperty(AnalysisOption option, Writer w) {
		def type, id = option.id.toLowerCase()

		if (option.argInputType) {
			type = "(file)"
		} else if (option.argName && option instanceof BooleanAnalysisOption) {
			type = "(boolean)"
		} else if (option.argName && option instanceof IntegerAnalysisOption) {
			type = "(boolean)"
		} else if (option.argName) {
			type = "(string)"
		} else {
			type = "(boolean)"
		}

		w.write "#\n"
		w.write "#$id $type\n"
		if (option.description) w.write "#${option.description} \n"
		w.write "#\n"
		w.write "$id = \n\n"
	}

	private static void addAnalysisOptionsToCliBuilder(List<AnalysisOption> options, CliBuilder cli) {
		convertAnalysisOptionsToCliOptions(options).each { cli << it }
	}

	private static String desc(AnalysisOption option) {
		option.validValues ? "$option.description Valid values: ${option.validValues.join(", ")}" : option.description
	}

	/**
	 * Creates the cli args from the respective analysis options (the ones with their cli property set to true).
	 */
	static List<Option> convertAnalysisOptionsToCliOptions(List<AnalysisOption> options) {
		options.collect { AnalysisOption option ->
			if (option.multipleValues) {
				def o = new Option(option.optName, option.name, true, desc(option))
				o.setArgs(Option.UNLIMITED_VALUES)
				o.setArgName(option.argName)
				return o
			} else if (option.argName) {
				def o = new Option(option.optName, option.name, true, desc(option))
				o.setArgName(option.argName)
				return o
			} else {
				return new Option(option.optName, option.name, false, desc(option))
			}
		}
	}
}
