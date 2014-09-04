package doop

import org.apache.commons.logging.Log
import org.apache.log4j.*

import java.lang.reflect.Method

/**
 * Various helper methods.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 30/8/2014
 */
class Helper {

    /**
     * Initializes Log4j
     */
    static void initLogging(String logLevel, String logDir, boolean console) {
        File dir = new File(logDir)
        if (!dir.exists()) dir.mkdir()

        String logFile =  "${logDir}/jdoop.log"

        PatternLayout layout = new PatternLayout("%d [%t] %-5p %c - %m%n")
        Logger root = Logger.getRootLogger()
        root.setLevel(Level.toLevel(logLevel, Level.WARN))
        DailyRollingFileAppender appender = new DailyRollingFileAppender(layout, logFile, "'.'yyyy-MM-dd")
        root.addAppender(appender)

        if (console) {
            root.addAppender(new ConsoleAppender(new PatternLayout("%m%n")))
        }
    }

    /**
     * Loads the given properties file
     */
    static Properties loadProperties(String file) {
        Properties properties = new Properties()
        properties.load(new BufferedReader(new FileReader(file)))
        return properties
    }

    /**
     * Executes the given closure using the supplied logger to report its duration
     */
    static void execWithTiming(Log logger, Closure closure) {
        long now = System.currentTimeMillis()
        try {
            closure.call()
        }
        catch(e) {
            throw e
        }

        //we measure the time only in error-free cases
        long duration = System.currentTimeMillis() - now
        logger.info "elapsed time: ${duration / 1000} sec"

    }

    /**
     * Checks that the given file exists or throws the given message
     */
    static File checkFileOrThrowException(String file, String message) {
        if (!file) throw new RuntimeException(message)

        File f = new File(file)
        if (!f.exists() || !f.isFile() || !f.canRead()) {
            throw new RuntimeException(message)
        }
        return f
    }

    /**
     * Checks that the given dir exists or throws the given message
     */
    static File checkDirectoryOrThrowException(String dir, String message) {
        if (!dir) throw new RuntimeException(message)

        File f = new File(dir)
        if (!f.exists() || !f.isDirectory()) {
            throw new RuntimeException(message)
        }
        return f
    }

    /**
     * Executes the given command as an external process, setting its environment to the supplied Map
     */
    static void execCommand(String command, Map<String, String> env) {
        List<String> envList = env?.collect { Map.Entry entry -> "${entry.key}=${entry.value}" }
        Process process = command.execute(envList, null)
        process.waitForProcessOutput(System.out as OutputStream, System.err as OutputStream)
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command exited with non-zero status: $command")
        }
    }

    /**
     * Executes the given Java main class using the supplied class loader.
     */
    static void execJava(ClassLoader cl, String mainClass, String[] params) {
        runWithClassLoader(cl) {
            invokeMainMethod(mainClass, params)
        }
    }


    /**
     * Runs the closure in the current thread using the specified class loader
     * @param cl - the class loader to use
     * @param closure - the closure to run
     */
    static void runWithClassLoader(ClassLoader cl, Closure closure) {
        Thread currentThread = Thread.currentThread()
        ClassLoader oldLoader = currentThread.getContextClassLoader()
        currentThread.setContextClassLoader(cl)
        try {
            closure.call()
        } catch (e) {
            throw new RuntimeException(e.getMessage(), e)
        }
        finally {
            currentThread.setContextClassLoader(oldLoader)
        }
    }

    /**
     * Invokes the main method of the given mainClass, passing the supplied params.
     */
    static void invokeMainMethod(String mainClass, String[] params) {
        Class[] parameterTypes = [String[].class]
        Object[] args = [params]
        Method main = Class.forName(mainClass).getMethod("main", parameterTypes)
        main.invoke(null, args)
    }
}
