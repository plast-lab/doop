package org.clyze.doop.core

import groovy.cli.commons.OptionAccessor
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.util.function.Predicate
import org.clyze.analysis.AnalysisOption
import org.clyze.analysis.BooleanAnalysisOption
import org.clyze.analysis.IntegerAnalysisOption
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.utils.FileOps
import org.clyze.utils.JHelper

/**
 * Doop initialization and supported options.
 */
@Log4j
@CompileStatic
class Doop {

	static final String LOG_NAME = 'doop.log'

	static final List<String> OPTIONS_EXCLUDED_FROM_ID_GENERATION = [
			"LOGICBLOX_HOME",
			"LD_LIBRARY_PATH",
			"BLOXBATCH",
			"BLOX_OPTS",
			"CACHE",
			"PLATFORMS_LIB"
	]

	// Not the best pattern, but limits the source code size :)
	static String doopHome
	static String doopOut
	static String doopCache
	static String doopLog
	static String doopTmp
	static String lbLogicPath
	static String souffleLogicPath
	static String souffleAnalysesCache
 	static String lbAnalysesPath
	static String souffleAnalysesPath
	static boolean initialized = false

	/**
	 * Initializes Doop.
	 * @param homePath  The Doop home directory (sets the doopHome variable, required).
	 * @param outPath	The Doop out directory (sets the doopOut variable, optional, defaults to 'out' under doopHome).
	 * @param cachePath The Doop cache directory (sets the doopCache variable, optional, defaults to 'cache' under doopHome).
	 * @param logPath	The Doop log directory (sets the doopLog variable, optional, defaults to 'build/logs' under doopHome).
	 * @param tmpPath	The Doop tmp directory (sets the doopTmp variable, optional, defaults to 'tmp' under doopHome).
	 */
	static void initDoop(String homePath, String outPath, String cachePath, String logPath, String tmpPath) {
		if (initialized)
			log.warn "WARNING: Doop has already been initialized!"
		else
			initialized = true

		doopHome = homePath
		if (!doopHome) throw new RuntimeException("DOOP_HOME environment variable is not set")
		FileOps.findDirOrThrow(doopHome, "DOOP_HOME environment variable is invalid: $doopHome")

		doopOut = outPath ?: "$doopHome/out"
		doopCache = cachePath ?: "$doopHome/cache"
		doopLog = logPath ?: "$doopHome/build/logs"
		doopTmp = tmpPath ?: "$doopHome/build/tmp"
		lbLogicPath = "$doopHome/lb-logic"
		souffleLogicPath = "$doopHome/souffle-logic"
		souffleAnalysesCache = "$doopCache/souffle-analyses"
		lbAnalysesPath = "$lbLogicPath/analyses"
		souffleAnalysesPath = "$souffleLogicPath/analyses"

		[doopOut, doopCache, doopLog, doopTmp, souffleAnalysesCache].each {
			def f = new File(it)
			f.mkdirs()
			FileOps.findDirOrThrow(f, "Could not create directory: $it")
		}
	}

	/**
	 * Initializes Doop using the default environment variables.
	 */
	static void initDoopFromEnv() {
		Doop.initDoop(System.getenv("DOOP_HOME"), System.getenv("DOOP_OUT"), System.getenv("DOOP_CACHE"), System.getenv("DOOP_LOG"), System.getenv("DOOP_TMP"))
	}

	/**
	 * Initializes Doop (and its logging machinery) using the default environment variables.
	 */
	static void initDoopWithLoggingFromEnv() {
		initDoopFromEnv()

		try {
			String logLevel = 'INFO' // Logger.rootLogger.level.toString()
			JHelper.tryInitLogging(logLevel, doopLog, true, LOG_NAME)
			log.debug "logLevel=${logLevel}"
		} catch (IOException ex) {
			System.err.println("WARNING: Could not initialize logging")
			throw DoopErrorCodeException.error15()
		}

		log.debug "Doop initialized with: doopOut = ${doopOut}, doopCache = ${doopCache}, doopLog = ${doopLog}, doopTmp = ${doopTmp}, souffleAnalysesCache = ${souffleAnalysesCache}, logicPath = ${lbLogicPath}, souffleLogicPath = ${souffleLogicPath}, analysesPath = ${lbAnalysesPath}"
	}

	/**
	 * Creates the default analysis options.
	 * @return Map < String , AnalysisOptions > .
	 */
	static Map<String, AnalysisOption<?>> createDefaultAnalysisOptions() {
		new DoopAnalysisFamily().supportedOptions().collectEntries { [(it.id): it.clone()] }
	}

	/**
	 * Creates the analysis options by overriding the default options with the
	 * ones contained in the given CLI options. An option is set only if
	 * filtered (the supplied filter returns true for the option).
	 * @param cli - the CLI option accessor.
	 * @param filter - optional filter to apply before setting the option.
	 * @return the default analysis options overridden by the values contained in the CLI option accessor.
	 */
	static Map<String, AnalysisOption<?>> overrideDefaultOptionsWithCLI(OptionAccessor cli, Predicate<AnalysisOption> filter) {
		def options = createDefaultAnalysisOptions()
		overrideOptionsWithCLI(options, cli, filter)
		return options
	}

	/**
	 * Creates the analysis options by overriding the default options with the
	 * ones contained in the given properties and CLI options. A CLI option
	 * superseeds a property one. An option is set only if filtered (the
	 * supplied filter returns true for the option).
	 * @param props - the properties.
	 * @param cli - the CLI option accessor.
	 * @param filter - optional filter to apply before setting the option.
	 * @return the default analysis options overridden by the values contained in the properties.
	 */
	static Map<String, AnalysisOption<?>> overrideDefaultOptionsWithPropertiesAndCLI(Properties properties,
	                                                                              OptionAccessor cli,
	                                                                              Closure<Boolean> filter) {
		Map<String, AnalysisOption<?>> options = createDefaultAnalysisOptions()
		overrideOptionsWithProperties(options, properties, filter)
		overrideOptionsWithCLI(options, cli, filter)
		return options
	}

	/**
	 * Overrides the values of the map (the options values) with the values
	 * contained in the properties. An option is set only if filtered (the
	 * supplied filter returns true for the option).
	 * @param options - the options to override.
	 * @param properties - the properties to use.
	 * @param filter - the filter to apply.
	 * @return the original map of options with its values overridden by the ones contained in the properties.
	 */
	static void overrideOptionsWithProperties(Map<String, AnalysisOption<?>> options,
	                                          Properties properties,
	                                          Closure<Boolean> filter) {
		if (properties && properties.size() > 0) {
			properties.each { k, v ->
				String key = k as String
				String value = v as String
				AnalysisOption option = options.get(key.toUpperCase())
				if (option && value && value.trim().length() > 0) {
					boolean filtered = filter ? filter.call(option) : true
					if (filtered) {
						if (option.argName) {
							option.value = (value != "null" ? value : null)
						} else {
							option.value = Boolean.parseBoolean(value)
						}
					}
				}
			}
		}
	}

	/**
	 * Overrides the values of the map (the options values) with the values
	 * contained in the CLI options. An option is set only if filtered (the
	 * supplied filter returns true for the option).
	 * @param options - the options to override.
	 * @param properties - the properties to use.
	 * @param filter - the filter to apply.
	 * @return the original map of options with its values overridden by the ones contained in the CLI options.
	 */
	static void overrideOptionsWithCLI(Map<String, AnalysisOption<?>> options, OptionAccessor cli,
									   Predicate<AnalysisOption> filter) {
		options.values().each { AnalysisOption option ->
			def name = option.name
			if (name) {
				log.debug "Processing $name"
				// NOTE: Obscure cli builder feature: to get the value of a cl option
				// as a List, you need to append an s to its short name
				def optionValue = option.multipleValues ? cli[("${name}s")] : cli[(name)]
				if (optionValue) { //Only true-ish values are of interest (false or null values are ignored)
					if (filter ? filter.test(option) : true) {
						// If the cl option has an argument, its value defines the value of the
						// respective analysis option
						if (option.argName) {
							if (option instanceof BooleanAnalysisOption)
								option.value = (optionValue as String).toBoolean()
							else if (option instanceof IntegerAnalysisOption)
								option.value = (optionValue as String).toInteger()
							else
								option.value = optionValue
						}
						// If the cl option has no argument and it's a boolean flag which
						// is now set to true (all boolean options are false by default)
						else {
							option.value = true
						}
					}
				}
			}
		}
	}

}
