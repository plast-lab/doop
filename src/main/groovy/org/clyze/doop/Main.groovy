package org.clyze.doop

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis
import org.clyze.doop.soot.DoopErrorCodeException
import org.clyze.utils.FileOps
import org.clyze.utils.Helper
import org.codehaus.groovy.runtime.StackTraceUtils

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The entry point for the standalone doop app.
 */
@CompileStatic
@Log4j
class Main {

	// Allow access to the analysis object from external code
	static DoopAnalysis analysis

	static void main(String[] args) {

		Doop.initDoop(System.getenv("DOOP_HOME"), System.getenv("DOOP_OUT"), System.getenv("DOOP_CACHE"))
		Helper.initLogging("INFO", "${Doop.doopHome}/build/logs", true)

		try {
			// The builder for displaying usage should not include non-standard flags
			def usageBuilder = CommandLineAnalysisFactory.createCliBuilder(false)
			// The builder for displaying usage of non-standard flags
			def nonStandardUsageBuilder = CommandLineAnalysisFactory.createNonStandardCliBuilder()
			// The builder for actually parsing the arguments needs to include non-standard flags
			def builder = CommandLineAnalysisFactory.createCliBuilder(true)

			if (!args) {
				usageBuilder.usage()
				return
			}

			def argsToParse
			def bloxOptions
			def index = args.findIndexOf { it == "--" }
			if (index == -1) {
				argsToParse = args
				bloxOptions = null
			} else {
				argsToParse = args[0..(index - 1)]
				bloxOptions = args[(index + 1)..(args.length - 1)].join(' ')
			}

			def cli = builder.parse(argsToParse)
			if (!cli) {
				// We assume usage has already been displayed by the CliBuilder.
				return
			} else if (cli.arguments().size() != 0) {
				log.info "Invalid argument specified: " + cli.arguments()[0]
				usageBuilder.usage()
				return
			} else if (cli['h']) {
				usageBuilder.usage()
				return
			} else if (cli['X']) {
				nonStandardUsageBuilder.usage()
				return
			}

			String userTimeout
			try {
				if (cli['p']) {
					//create analysis from the properties file & the cli options
					def file = cli['p'] as String
					def f = FileOps.findFileOrThrow(file, "Not a valid file: $file")
					def propsBaseDir = f.absoluteFile.parentFile
					def props = FileOps.loadProperties(f)

					changeLogLevel(cli['L'] ?: props.getProperty("level"))
					userTimeout = cli['t'] ?: props.getProperty("timeout")
					analysis = new CommandLineAnalysisFactory().newAnalysis(propsBaseDir, props, cli)
				} else {
					changeLogLevel(cli['L'])
					userTimeout = cli['t']
					analysis = new CommandLineAnalysisFactory().newAnalysis(cli)
				}
			} catch (e) {
				log.error e.message
				usageBuilder.usage()
				return
			}

			analysis.options.BLOX_OPTS.value = bloxOptions

			if (userTimeout != "false")
				analysis.options.TIMEOUT.value = parseTimeoutOrDefault(userTimeout, analysis.options.TIMEOUT.value as int)
			log.info "Using a timeout of ${analysis.options.TIMEOUT.value} min."

			def executorService = Executors.newSingleThreadExecutor()
			try {
				executorService.submit(new Runnable() {
					@Override
					void run() {
						try {
							log.debug analysis
							analysis.run()
							new CommandLineAnalysisPostProcessor().process(analysis)
						} catch (DoopErrorCodeException ex) {
							// Don't continue with the analysis.
						}
					}
				}).get(analysis.options.TIMEOUT.value as int, TimeUnit.MINUTES)
			} catch (TimeoutException te) {
				log.error "Timeout has expired (${analysis.options.TIMEOUT.value} min)."
			} finally {
				executorService.shutdownNow()
			}
		} catch (e) {
			e = (e.getCause() ?: e)
			e = StackTraceUtils.deepSanitize e
			log.error(e.message, e)
		}
	}

	private static void changeLogLevel(def logLevel) {
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
					log.info "Invalid log level: $logLevel - using default (info)"
			}
		}
	}

	private static int parseTimeoutOrDefault(String userTimeout, int defaultTimeout) {
		try {
			def timeout = Integer.parseInt(userTimeout)
			if (timeout <= 0) throw new Exception()
			return timeout
		} catch (all) {
			log.info "Invalid user supplied timeout: `$userTimeout` - fallback to default."
			return defaultTimeout
		}
	}
}
