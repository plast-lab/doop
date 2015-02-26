package doop.core
import org.apache.commons.cli.Option
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.log4j.*

import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
/**
 * Various helper methods.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 30/8/2014
 */
class Helper {

    private static final FileFilter ALL_FILES = [
        accept: { File f -> f.isFile() }
    ] as FileFilter

    private static final FileFilter ALL_FILES_AND_DIRECTORIES = [
        accept: { File f -> true }
    ] as FileFilter

    /**
     * Initializes Log4j (logging framework).
     * Log statements are written to log file that is daily rolled.
     * Optionally, the log statements can be also written to the console (standard output).
     * @param logLevel - the log level to use
     * @param logDir - the directory to place the log file
     * @param console - indicates whether log statements should be also written to the standard output.
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
     * Initializes Log4j (logging framework).
     * Log statements are written to the the console (standard output).
     * @param logLevel - the log level to use
     */
    static void initConsoleLogging(String logLevel){
        Logger root = Logger.getRootLogger()
        root.setLevel(Level.toLevel(logLevel, Level.WARN))
        root.addAppender(new ConsoleAppender(new PatternLayout("%m%n")))
    }

    static FilenameFilter extensionFilter(String extension) {
        def filter = [
            accept: {File f, String name ->
                String ext = FilenameUtils.getExtension(name)
                return ext == extension
            }
        ] as FilenameFilter

        return filter
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
     * Executes the given closure using the supplied logger to report its duration in seconds. It also returns the
     * duration.
     */
    static long execWithTiming(Log logger, Closure closure) {
        long now = System.currentTimeMillis()
        try {
            closure.call()
        }
        catch(e) {
            throw e
        }

        //we measure the time only in error-free cases
        long duration = (System.currentTimeMillis() - now) / 1000
        logger?.info "elapsed time: $duration sec"
        return duration
    }

    /**
     * Checks that the given file exists or throws the given message
     */
    static File checkFileOrThrowException(String file, String message) {
        if (!file) throw new RuntimeException(message)
        return checkFileOrThrowException(new File(file), message)
    }

    /**
     * Checks that the given file exists or throws the given message
     */
    static File checkFileOrThrowException(File f, String message) {
        if (!f) throw new RuntimeException(message)

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
     * Starts the given command as an external process, setting its environment to the supplied Map.
     * The method invokes each command through the shell (/bin/bash).
     */
    static Process startExternalProcess(String command, Map<String, String> env, boolean redirectErrorStream) {
        Logger.getRootLogger().debug "Executing $command"

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command)
        pb.redirectErrorStream(redirectErrorStream)
        Map<String, String> environment = pb.environment()
        environment.clear()
        environment.putAll(env)

        return pb.start()
    }

    /**
     * Executes the given command in the given environment, waiting for the process to complete.
     * The method redirects the command's output and error streams to System.out and System.err respectively.
     */
    static void execCommand(String command, Map<String, String> env) {

        Process process = startExternalProcess(command, env, false)

        process.waitForProcessOutput(System.out as OutputStream, System.err as OutputStream)
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command exited with non-zero status:\n $command")
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

    /**
     * Returns a list of the names of the available analyses in the given doop logic directory
     */
    static List<String> namesOfAvailableAnalyses(String doopLogicDir) {
        List<String> analyses = []
        new File(Doop.doopLogic).eachDir { File dir ->
            if (dir.getName().indexOf("sensitive") != -1 ) {
                File f = new File(dir, "analysis.logic")
                if (f.exists() && f.isFile()) {
                    analyses.push(dir.getName())
                }
            }
        }
        return analyses
    }
	
	/**
     * Returns a set of the packages contained in the given jar.
     * Any classes that are not included in packages are also retrieved.
     */
    static Set<String> getPackages(File jar) {

        ZipFile zip = new ZipFile(jar)
        Enumeration<? extends ZipEntry> entries = zip.entries()
        List<ZipEntry> classes = entries?.findAll { ZipEntry entry ->
            entry.getName().endsWith(".class")
        }
        List<String> packages = classes.collect { ZipEntry entry ->
            String entryName = entry.getName()
            if (entryName.indexOf("/") > 0)
                return FilenameUtils.getPath(entry.getName()).replace('/' as char, '.' as char) + '*'
            else
                return FilenameUtils.getBaseName(entryName)
        }

        packages = packages.unique()

        return (packages as Set)
    }
	
	/**
	 * Generates a checksum of the input string (in hex) using the supplied algorithm (SHA-256, MD5, etc).
	 */
	static String checksum(String s, String algorithm) {
		MessageDigest digest = MessageDigest.getInstance(algorithm)
		byte[] bytes = digest.digest(s.getBytes("UTF-8"))
		BigInteger number = new BigInteger(1, bytes)
		String checksum = number.toString(16)
		int len = checksum.length()
		while (len < 32) {
			checksum = "0" + checksum
		}
		return checksum
	}


    /**
     *  Moves the contents of the src directory to dest (as in: mv src/* dest).
     */
    static void moveDirectoryContents(File src, File dest) {
        FileUtils.copyDirectory(src, dest, ALL_FILES_AND_DIRECTORIES)
        FileUtils.cleanDirectory(src)
    }

    /**
     * Copies the contents of the src directory to dest (as in: cp -R src/* dest).
     */
    static void copyDirectoryContents(File src, File dest) {
        FileUtils.copyDirectory(src, dest, ALL_FILES_AND_DIRECTORIES)
    }

    /**
     * Writes the given string to the given file.
     */
    static File writeToFile(File f, String s) {
        f.withWriter { Writer w ->
            w.write s
        }
        return f
    }

    /**
     * Returns the stack trace of an exception as String.
     */
    static String stackTraceToString(Throwable t) {
        StringBuilder sb = new StringBuilder()
        t.getStackTrace().each { StackTraceElement elem ->
            sb.append(elem.toString()).append('\n')
        }
        return sb.toString()
    }

    /**
     * Creates the default properties file containing all the supported analysis options, with their default values.
     */
    static void createDefaultProperties(File f) {

        //Find all cli options and sort them by name
        List<AnalysisOption> cliOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option ->
            option.cli
        }.sort { AnalysisOption option ->
            option.name
        }

        //Put the "main" options first
        cliOptions = cliOptions.findAll { AnalysisOption option -> !option.isAdvanced } +
                     cliOptions.findAll { AnalysisOption option -> option.isAdvanced }

        f.withWriter { Writer w ->

            cliOptions.each { AnalysisOption option ->
                writeAsProperty(option, w)
            }
        }
    }

    /**
     * Writes the given analysis option to the given writer using the standard Java syntax for properties files.
     */
    private static void writeAsProperty(AnalysisOption option, Writer w) {
        def type

        if (option.isFile) {
            type = "(file)"
        }
        else if (option.argName) {
            type = "(string)"
        }
        else {
            type = "(boolean)"
        }

        if (option.description) {
            w.write "#${option.id} $type - ${option.description} \n"
        }
        else {
            w.write "#${option.id} $type\n"
        }

        w.write "${option.id} = \n\n"
    }

    /**
     * Converts a list of analysis options to a list of cli options.
     */
    static List<Option> convertAnalysisOptionsToCliOptions(List<AnalysisOption> options) {
        return options.collect { AnalysisOption option ->
            if (option.id == "DYNAMIC") {
                //Special handling of DYNAMIC option
                Option o = new Option('d', option.name, true, option.description)
                o.setArgs(Option.UNLIMITED_VALUES)
                o.setArgName(option.argName)
                o.setValueSeparator(',' as char)
                return o
            }
            else if (option.argName) {
                Option o = new Option(null, option.name, true, option.description)
                o.setArgName(option.argName)
                return o
            }
            else {
                return new Option(null, option.name, false, option.description)
            }
        }
    }

    /**
     * Adds the list of analysis options to the cli builder.
     * @param options - the list of AnalysisOption items to add.
     * @param cli - the cli builder.
     */
    static void addAnalysisOptionsToCliBuilder(List<AnalysisOption> options, CliBuilder cli) {
        convertAnalysisOptionsToCliOptions(options).each { cli << it}
    }
}
