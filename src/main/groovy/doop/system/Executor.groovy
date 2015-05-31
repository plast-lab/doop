package doop.system

import groovy.transform.TypeChecked;

@TypeChecked
class Executor
{
    static final Closure STDOUT_PRINTER = { String line -> println line }

    Map<String, String> environment

    Executor(Map<String, String> environment)
    {
        this.environment = environment
    }

    void execute(String commandLine, Collection<String> ignoredWarnings=null, Closure outputLineProcessor = STDOUT_PRINTER)
    {
        def pb = new ProcessBuilder("/bin/bash", "-c", commandLine)
        def environment = pb.environment()
        environment.clear()
        environment.putAll(this.environment)
        def process = pb.start()

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
}
