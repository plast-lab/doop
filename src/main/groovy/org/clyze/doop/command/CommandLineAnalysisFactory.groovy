package org.clyze.doop.command

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
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
import org.clyze.utils.JHelper
import org.clyze.utils.OS

/**
 * A factory for creating Analysis objects from the command line.
 */
@CompileStatic
@Log4j
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
	static final String USAGE = "doop -i <INPUT> -a <NAME> [OPTION]..."
	static final int WIDTH = terminalWidth

	static final AnalysisFamily FAMILY = new DoopAnalysisFamily()

	private static int getTerminalWidth() {
		// Check environment variable.
		Integer w1 = processTerminalWidth(System.getenv('COLUMNS'))
		if (w1 != null)
			return w1
		// Check tput on Linux/macOS.
		if (!OS.win) {
			try {
				String tputCols = null
				JHelper.runCommand('tput cols', null, { tputCols = it })
				Integer w2 = processTerminalWidth(tputCols)
				if (w2 != null)
					return w2
			} catch (ignored) { }
		}
		// Default value.
		return 120
	}

	private static Integer processTerminalWidth(String s) {
		if (s != null) {
			try {
				int columns = Integer.valueOf(s)
				if (columns > 40)
					return columns
			} catch (ignored) { }
		}
		return null
	}

	/**
	 * Processes the cli args and generates a new analysis.
	 */
	DoopAnalysis newAnalysis(OptionAccessor cli) {
		def options = Doop.overrideDefaultOptionsWithCLI(cli) { it.cli }
		// Get the id of the analysis (short option: id)
		options.USER_SUPPLIED_ID.value = cli['id'] ?: null
		// Get the name of the analysis (short option: a)
		options.ANALYSIS.value = cli['a']
		// Get the inputFiles of the analysis (short option: i)
		if (options.INPUT_ID.value && cli['is']) {
			log.warn "WARNING: Ignoring inputs (--${options.INPUT_ID.name})."
		}
		options.INPUTS.value = (!options.INPUT_ID.value && cli['is']) ? cli['is'] : []
		// Get the libraryFiles of the analysis (short option: l)
		if (options.INPUT_ID.value && cli['ls']) {
			log.warn "WARNING: Ignoring libraries (--${options.INPUT_ID.name})."
		}
		options.LIBRARIES.value = (!options.INPUT_ID.value && cli['ls']) ? cli['ls'] : []

		newAnalysis(FAMILY, options)
	}

	/**
	 * Processes the properties and the cli and generates a new analysis.
	 */
	DoopAnalysis newAnalysis(File propsBaseDir, Properties props, OptionAccessor cli) {
		def options = Doop.overrideDefaultOptionsWithPropertiesAndCLI(props, cli) { AnalysisOption it -> it.cli }
		// Get the id of the analysis (short option: id)
		options.USER_SUPPLIED_ID.value = cli['id'] ?: ["id", "USER_SUPPLIED_ID"].findResult { props.getProperty(it) }
		// Get the name of the analysis (short option: a)
		options.ANALYSIS.value = cli['a'] ?: ["analysis", "ANALYSIS"].findResult { props.getProperty(it) }
		// Get the inputFiles of the analysis (short option: i)
		options.INPUTS.value = cli['is'] ?: filesFromProperty(propsBaseDir, props, ["inputFiles", "INPUTS"])
		// Get the libraryFiles of the analysis (short option: l)
		options.LIBRARIES.value = cli['ls'] ?: filesFromProperty(propsBaseDir, props, ["libraryFiles", "LIBRARIES"])
		// Get the heapFiles of the analysis (long option: heapdl)
		options.HEAPDLS.value = cli['heapdls'] ?: filesFromProperty(propsBaseDir, props, ["heapFiles", "HEAPDLS"])

		newAnalysis(FAMILY, options)
	}

	private static List<String> filesFromProperty(File propsBaseDir, Properties props, List<String> possibleIDs) {
		return possibleIDs.findResult { p ->
			def prop = props.getProperty(p)
			if (!prop || prop == "[]") return null

			// Files, if relative, are being resolved via the propsBaseDir or later if they are URLs
			return prop.split().collect {
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
		} ?: []
	}

	static CliBuilder createCliBuilder() {
		List<AnalysisOption<?>> cliOptions = FAMILY.supportedOptions().findAll { it.cli }

		def cli = new CliBuilder(
				parser: new GnuParser(),
				formatter: new HelpGroupFormatter(),
				usage: USAGE,
				width: WIDTH,
		)

		([	new Tuple5('h', 'help', 'SECTION', 'Display help and exit.', true),
			new Tuple5('L', 'level', 'LOG_LEVEL', LOGLEVEL, false),
			new Tuple5('p', 'properties', 'PROPERTIES', PROPS, false),
			new Tuple5('t', 'timeout', 'TIMEOUT', TIMEOUT, false)
		] as List<Tuple5<String, String, String, String, Boolean>>).each {
			Option opt = new Option(it.v1, it.v2, true, it.v4)
			opt.argName = it.v3
			opt.optionalArg = it.v5
			cli.options.addOption(opt)
		}
		cli.options.addOption(new Option('v', 'version', false, 'Display version and exit.'))

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

	private static void addAnalysisOptionsToCliBuilder(List<AnalysisOption<?>> options, CliBuilder cli) {
		convertAnalysisOptionsToCliOptions(options).each { cli.options.addOption(it) }
	}

	private static String desc(AnalysisOption option) {
		option.validValues ? validValues(option.description, option.validValues as Collection<String>) : option.description
	}

	static String validValues(String description, Collection<String> validValues) {
		return "$description Valid values: ${validValues.join(", ")}"
	}

	/**
	 * Creates the cli args from the respective analysis options (the ones with their cli property set to true).
	 */
	static List<GOption> convertAnalysisOptionsToCliOptions(List<AnalysisOption<?>> options) {
		options.collect { AnalysisOption option ->
			if (option.multipleValues) {
				def o = new GOption(option.optName, option.name, true, desc(option), option.group)
				o.args = Option.UNLIMITED_VALUES
				o.argName = option.argName
				return o
			} else if (option.argName) {
				def o = new GOption(option.optName, option.name, true, desc(option), option.group)
				o.argName = option.argName
				return o
			} else {
				return new GOption(option.optName, option.name, false, desc(option), option.group)
			}
		}
	}
}
