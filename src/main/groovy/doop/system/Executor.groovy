package doop.system

import doop.core.Helper
import groovy.transform.TypeChecked
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;

@TypeChecked
class Executor {

    protected Log logger = LogFactory.getLog(getClass())

    static final Closure STDOUT_PRINTER = { String line -> println line }

    Map<String, String> environment

    Executor(Map<String, String> environment) {
        this.environment = environment
    }

    void execute(String workingDirectory,
                 String commandLine,
                 Collection<String> ignoredWarnings = null,
                 Closure outputLineProcessor = STDOUT_PRINTER) {

        def pb = new ProcessBuilder("/bin/bash", "-c", commandLine)
        if (workingDirectory) {
            File cwd = Helper.checkDirectoryOrThrowException(workingDirectory, "Working directory is invalid: $workingDirectory")
            pb.directory(cwd)
        }
        def environment = pb.environment()
        environment.clear()
        environment.putAll(this.environment)
        def process = pb.start()

        //Add shutdown hook that permits us to kill the child process when the JVM exits
        System.addShutdownHook {
            logger.debug("Running shutdown hook to terminate process: $commandLine")
            try {
                //If the process has terminated, this will return a value. If not, it will throw an exception.
                int exit = process.exitValue()
                logger.debug("Process has exited with value $exit: $commandLine")
            }
            catch(any) {
                //The process has not terminated, destroy it.
                logger.debug("Destroying process: $commandLine")
                process.destroy()
                logger.debug("Process destroyed: $commandLine")
            }
        }

        // Get its standard output and error streams as input streams
        final InputStream is = process.getInputStream()
        final InputStream es = process.getErrorStream()

        is.newReader().withReader { reader ->
            String nextLine;

            while ((nextLine = reader.readLine()) != null) {
                outputLineProcessor(nextLine.trim());
            }
        }

        // Wait for process to terminate
        def returnCode = process.waitFor();

        // Create an error string that contains everything in the stderr stream
        def errorMessages = es.getText();

        // Prune some redundant warnings
        for (warning in ignoredWarnings) {
            errorMessages = errorMessages.replaceAll(warning) { String _ -> "" }
        }

        // Print the remaining warnings
        if (!errorMessages.isAllWhitespace()) {
            System.err.print(errorMessages)
        }

        // Check return code and raise exception at failure indication
        if (returnCode != 0) {
            throw new RuntimeException("Command exited with non-zero status:\n $commandLine")
        }
    }

    void execute(String commandLine, Collection<String> ignoredWarnings=null, Closure outputLineProcessor = STDOUT_PRINTER) {
        execute(null, commandLine, ignoredWarnings, outputLineProcessor)
    }
}
