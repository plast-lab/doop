package org.clyze.doop

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis
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
		try {
			main2(args)
		} catch (e) {
			if (!(e instanceof DoopErrorCodeException))
				CommandLineAnalysisFactory.createCliBuilder().usage()
		}
	}

	/**
	 * Entry point when using Doop as a library.
	 *
	 * @param args					   the command line arguments that
	 *								   Doop should receive
	 * @throws DoopErrorCodeException  an exception with an error code
	 *								   that identifies points of failure
	 */
	static void main2(String[] args) throws DoopErrorCodeException {
		Doop.initDoopFromEnv()
		try {
			Helper.tryInitLogging("INFO", Doop.doopLog, true)
		} catch (IOException ex) {
			System.err.println("Warning: could not initialize logging")
			throw new DoopErrorCodeException(15)
		}

		try {
			// The builder for displaying usage and parsing the arguments
			def clidBuilder = CommandLineAnalysisFactory.createCliBuilder()

			if (!args) {
				clidBuilder.usage()
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

			def cli = clidBuilder.parse(argsToParse)
			if (!cli) {
				// We assume usage has already been displayed by the CliBuilder.
				return
			} else if (cli.arguments().size() != 0) {
				log.info "Invalid argument specified: " + cli.arguments()[0]
				clidBuilder.usage()
				return
			} else if (cli['h']) {
				clidBuilder.usage()
				return
			}

			String userTimeout
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
						} catch (DoopErrorCodeException e) {
							log.error(e.message)
							log.debug(e.message, e)
						} catch (e) {
							log.error "Generic exception $e"
							throw e
						}
					}
				}).get(analysis.options.TIMEOUT.value as int, TimeUnit.MINUTES)
			} catch (TimeoutException e) {
				log.error "Timeout has expired (${analysis.options.TIMEOUT.value} min)."
			} finally {
				executorService.shutdownNow()
			}
		} catch (e) {
			// DoopErrorCodeException is a special wrapper that must
			// be propagated above, so we don't extract its cause yet.
			if (e instanceof DoopErrorCodeException) {
				log.error(e.message)
				log.debug(e.message, e)
			} else {
				e = (e.cause ?: e)
				e = StackTraceUtils.deepSanitize e
				log.error(e.message, e)
			}
			throw e
		}
	}

	private static void changeLogLevel(def logLevel) {
		if (logLevel) {
			switch (logLevel) {
				case "debug":
					Logger.rootLogger.level = Level.DEBUG
					break
				case "info":
					Logger.rootLogger.level = Level.INFO
					break
				case "error":
					Logger.rootLogger.level = Level.ERROR
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
			log.info "Invalid user supplied timeout: `${userTimeout}` - fallback to default (${defaultTimeout})."
			return defaultTimeout
		}
	}
}
