package doop.preprocess

import groovy.transform.TypeChecked;

@TypeChecked
class Executor
{
    static final Closure STDOUT_PRINTER = { String line -> println line }

    static void execute(String commandLine, Collection<String> ignoredWarnings=null, Closure outputLineProcessor = STDOUT_PRINTER)
    {
        Process process = new ProcessBuilder("/bin/bash", "-c", commandLine).start()

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
        int returnCode = process.waitFor();

        // Create an error string that contains everything in the stderr stream
        String errorMessages = es.getText();

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
