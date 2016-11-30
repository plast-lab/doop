package org.clyze.doop.core

import java.lang.reflect.Method
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.cli.Option
import org.apache.log4j.*
import org.apache.commons.io.FilenameUtils

/**
 * Various helper methods.
 */
class Helper {

    /**
     * Initializes Log4j (logging framework).
     * Log statements are written to log file that is daily rolled.
     * Optionally, the log statements can be also written to the console (standard output).
     * @param logLevel - the log level to use
     * @param logDir - the directory to place the log file
     * @param console - indicates whether log statements should be also written to the standard output.
     */
    static void initLogging(String logLevel, String logDir, boolean console) {
        org.clyze.Helper.initLogging(logLevel, logDir, console)
    }

    /**
     * Initializes Log4j (logging framework).
     * Log statements are written to the the console (standard output).
     * @param logLevel - the log level to use
     */
    static void initConsoleLogging(String logLevel){
        org.clyze.Helper.initConsoleLogging(logLevel)
    }

    /**
     * Executes the given Java main class using the supplied class loader.
     */
    static void execJava(ClassLoader cl, String mainClass, String[] params) {
        //This is a better way to invoke the main method using a different
        //classloader (the runWithClassLoader/invokeMainMethod methods are
        //problematic and should be removed).
        Class theClass = Class.forName(mainClass, true, cl)
        Method mainMethod = theClass.getMethod("main", [String[].class] as Class[])
        mainMethod.invoke(null, [params] as Object[])
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
     * Returns a list of the names of the available analyses in the given doop analyses directory
     */
    static List<String> namesOfAvailableAnalyses(String doopAnalysesDir) {
        List<String> analyses = []
        new File(doopAnalysesDir).eachDir { File dir ->
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
                return o
            }
            else if (option.argName) {
                //Option accepts a String value
                Option o = new Option(null, option.name, true, option.description)
                o.setArgName(option.argName)
                return o
            }
            else {
                //Option is a boolean
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
        boolean noAnalysis = !cli.a, noJar = !cli.i
        boolean error = noAnalysis || noJar

        if (error)
            throw new RuntimeException("Missing required argument(s): " + (noAnalysis ? "a" : "") +
                                       (noJar ? (noAnalysis ? ", " : "") + "i" : ""))
    }

    /**
     * Parses the user supplied timeout.
     * @param userTimeout - the user supplied timeout.
     * @param defaultTimeout - the default timeout to use if userTimeout is invalid.
     * @return a positive integer.
     */
    static int parseTimeout(String userTimeout, int defaultTimeout) {
        int timeout = defaultTimeout
        try {
            timeout = Integer.parseInt(userTimeout)
        }
        catch(ex) {
            println "Using the default timeout ($timeout min)."
            return defaultTimeout
        }

        if (timeout <= 0) {
            println "Invalid user supplied timeout: $timeout - using the default ($defaultTimeout min)."
            return defaultTimeout
        }
        else {
            println "Using a timeout of $timeout min."
            return timeout
        }
    }
}
