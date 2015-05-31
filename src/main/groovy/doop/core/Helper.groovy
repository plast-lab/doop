package doop.core
import doop.system.Executor
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.cli.Option
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.log4j.*
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

        String logFile =  "${logDir}/doop.log"

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
        File f = checkFileOrThrowException(file, "Not a valid file: $file")
        Properties props = new Properties()
        f.withReader { BufferedReader r -> props.load(r)}
        return props
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
     * Checks that the given list of files exist.
     */
    static List<String> checkFiles(List<String> files) {
        files.each { String file ->
            checkFileOrThrowException(file, "File is invalid: $file")
        }
        return files
    }

    /**
     * Checks that the given dir exists or throws the given message
     */
    static File checkDirectoryOrThrowException(String dir, String message) {
        if (!dir) throw new RuntimeException(message)
        return checkDirectoryOrThrowException(new File(dir), message)
    }

    /**
     * Checks that the given dir exists or throws the given message
     */
    static File checkDirectoryOrThrowException(File dir, String message) {
        if (!dir) throw new RuntimeException(message)

        if (!dir.exists() || !dir.isDirectory()) {
            throw new RuntimeException(message)
        }
        return dir
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
        return toHex(digest.digest(s.getBytes("UTF-8")))
    }

    /**
     * Generates a checksum of the input file (in hex) using the supplied algorithm.
     */
    static String checksum(File f, String algorithm) {
        return f.withInputStream { InputStream input ->
            return checksum(input, algorithm)
        }
    }

    /**
     * Generates a checksum of the input stream (in hex) using the supplied algorithm.
     */
    static String checksum(InputStream input, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm)
        byte[] bytes = new byte[4096]
        int bytesRead
        while ((bytesRead = input.read(bytes)) != -1) {
            digest.update(bytes, 0, bytesRead)
        }
        return toHex(digest.digest())
    }

    /**
     * Returns the hex string of the input bytes.
     */
    private static String toHex(byte[] bytes) {
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
     * Append the contents of the second file at the end of the first one.
     */
    static void appendAtFirst(Analysis analysis, String firstPath, String secondPath) {
        File tmpFile = new File(FileUtils.getTempDirectory(), "tmpFile")
        String tmpFilePath = tmpFile.getCanonicalPath()
        new Executor(analysis.commandsEnvironment).execute("cpp -P $secondPath -include $firstPath $tmpFilePath")
        FileUtils.copyFile(tmpFile, new File(firstPath))
        FileUtils.deleteQuietly(tmpFile)
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

    /**
     * Checks that the mandatory options are present in the cli options.
     * @param cli the cli options accessor
     */
    static void checkMandatoryArgs(OptionAccessor cli) {
        boolean noAnalysis = !cli.a, noJar = !cli.j
        boolean error = noAnalysis || noJar

        if (error)
            throw new RuntimeException("Missing required argument(s): " + (noAnalysis ? "a" : "") +
                                       (noJar ? (noAnalysis ? ", " : "") + "j" : ""))
    }

    /**
     * Checks that the mandatory options are present in the properties.
     * @param props - the properties
     */
    static void checkMandatoryProps(Properties props) {
        boolean noAnalysis = !props.getProperty("analysis")?.trim()
        boolean noJar = !props.getProperty("jar")?.trim()
        boolean error = noAnalysis || noJar

        if (error)
            throw new RuntimeException("Missing required properties: " + (noAnalysis ? "analysis" : "") +
                                       (noJar ? (noAnalysis ? ", " : "") + "jar" : ""))
    }


    static boolean isMustPointTo(String name) {
        return "must-point-to".equals(name)
    }
}
