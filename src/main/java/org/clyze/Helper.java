package org.clyze;

import java.io.IOException;
import java.io.File;
import org.apache.log4j.*;

/**
 * Various helper methods.
 */
public class Helper {

	/**
	 * Initializes Log4j (logging framework).
	 * Log statements are written to log file that is daily rolled.
	 * Optionally, the log statements can be also written to the console (standard output).
	 * @param logLevel - the log level to use
	 * @param logDir - the directory to place the log file
	 * @param console - indicates whether log statements should be also written to the standard output.
	 */
	public static void initLogging(String logLevel, String logDir, boolean console) throws IOException {
		File dir = new File(logDir);
		if (!dir.exists()) dir.mkdir();

		String logFile = logDir + "/doop.log";

		PatternLayout layout = new PatternLayout("%d [%t] %-5p %c - %m%n");
		Logger root = Logger.getRootLogger();
		root.setLevel(Level.toLevel(logLevel, Level.WARN));
		DailyRollingFileAppender appender = new DailyRollingFileAppender(layout, logFile, "'.'yyyy-MM-dd");
		root.addAppender(appender);

		if (console) {
			root.addAppender(new ConsoleAppender(new PatternLayout("%m%n")));
		}
	}

	/**
	 * Initializes Log4j (logging framework).
	 * Log statements are written to the the console (standard output).
	 * @param logLevel - the log level to use
	 */
	public static void initConsoleLogging(String logLevel) throws IOException {
		Logger root = Logger.getRootLogger();
		root.setLevel(Level.toLevel(logLevel, Level.WARN));
		root.addAppender(new ConsoleAppender(new PatternLayout("%m%n")));
	}
}
