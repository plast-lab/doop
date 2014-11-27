package doop

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
     * Executes the given command as an external process, setting its environment to the supplied Map
     */
    static void execCommand(String command, Map<String, String> env) {
		Logger.getRootLogger().debug "Executing $command"
		List<String> envList = env?.collect { Map.Entry entry -> "${entry.key}=${entry.value}" }
        Process process = command.execute(envList, null)
        process.waitForProcessOutput(System.out as OutputStream, System.err as OutputStream)
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command exited with non-zero status\n: $command")
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
     * Returns a set of the packages contained in the given jar
     */
    static Set<String> getPackages(File jar) {

        ZipFile zip = new ZipFile(jar)
        Enumeration<? extends ZipEntry> entries = zip.entries()
        List<String> packages = entries?.findAll { ZipEntry entry ->
            entry.getName().endsWith(".class")
        }.collect { ZipEntry entry ->
            FilenameUtils.getPath(entry.getName()).replace('/' as char, '.' as char) + '*'
        }
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
}
