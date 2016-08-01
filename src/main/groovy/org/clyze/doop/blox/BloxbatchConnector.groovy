package org.clyze.doop.blox

import groovy.transform.TypeChecked
import java.util.regex.Pattern
import org.apache.log4j.Logger
import org.clyze.doop.util.process.CalledProcessException


/**
 * This class implements the WorkspaceConnector interface by spawning
 * a subprocess to a {@code bloxbatch} command.
 */
@TypeChecked
class BloxbatchConnector implements WorkspaceConnector
{
    private static final List<String> IGNORED_WARNINGS = [
        """\
        *******************************************************************
        Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.
        Please use 'lb' instead of 'bloxbatch'.
        *******************************************************************
        """
    ]*.stripIndent()


    /** The path to the workspace */
    private final String workspace

    /** The environment for running the external bloxbatch commands */
    private final Map<String, String> environment


    /**
     * Create a new workspace connector.
     *
     * @param workspace  the path to the workspace
     */
    public BloxbatchConnector(String workspace, Map<String,String> environment)
    {
        this.workspace = workspace
        this.environment = environment ?: new HashMap<String, String>()
    }

    public BloxbatchConnector(File workspace, Map<String,String> environment)
    {
        this(workspace.getPath(), environment)
    }

    protected void setEnvironment(Map<String,String> environment)
    {
        if (!environment) {
            throw new IllegalArgumentException()
        }

        this.environment.clear()
        this.environment.putAll(environment)
    }


    private void executeCommand(String commandLine, Closure outputLineProcessor)
    {
        Logger.getRootLogger().debug "Executing $commandLine"

        // Spawn the process to be run
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", commandLine)
        Map<String, String> environment = pb.environment()
        environment.clear()
        environment.putAll(this.environment.entrySet())
        Process process = pb.start()

        // Get its standard output and error streams as
        // input streams
        final InputStream is = process.getInputStream()
        final InputStream es = process.getErrorStream()

        is.newReader().withReader { reader ->
            String nextLine;

            // Append next line to queue
            while ((nextLine = reader.readLine()) != null) {
                outputLineProcessor(nextLine.trim());
            }
        }

        // Wait for process to terminate
        int returnCode = process.waitFor();

        // Create an error string that contains everything in the stderr stream
        String errorMessages = es.getText();

        // Prune some redundant warnings
        for (warning in IGNORED_WARNINGS.collect{ w -> Pattern.quote(w) }) {
            errorMessages = errorMessages.replaceAll(warning) { String _ -> "" }
        }

        // Print the remaining warnings
        if (!errorMessages.isAllWhitespace()) {
            System.err.print(errorMessages)
        }

        // Check return code and raise exception at failure indication
        if (returnCode != 0) {
            throw new CalledProcessException(returnCode, commandLine)
        }
    }


    private void executeCommand(String commandLine)
    {
        executeCommand(commandLine) { line ->
            ; // Do nothing
        }
    }


    @Override
    public void create(boolean shouldOverwrite)
    {
        String commandLine = "bloxbatch -db $workspace -create -blocks base "

        // Check if any existing database should be overwritten
        if (shouldOverwrite) {
            commandLine += "-overwrite "
        }

        executeCommand(commandLine)
    }


    @Override
    public void processQuery(String datalogString, Closure outputLineProcessor)
    {
        executeCommand("bloxbatch -db $workspace -query '$datalogString' ",
                       outputLineProcessor)
    }


    @Override
    public void processQuery(String datalogString, String printOpt, Closure outputLineProcessor)
    {
        String commandLine = "bloxbatch -db $workspace -query '$datalogString' "

        // Check if a print option was specified
        if (printOpt) {
            commandLine += "print $printOpt "
        }

        executeCommand(commandLine, outputLineProcessor)
    }


    @Override
    public Map<String,Integer> popCount(String... predicates)
    {
        // Construct command line
        def commandLine = "bloxbatch -db $workspace -popCount ${predicates.join(',')} "

        // Create empty map to hold counters
        def counters = [:]

        // Parse results and add counters to dictionary
        executeCommand(commandLine) { String line ->
            def num = line.tokenize(':').last()
            def predicate = line[0 .. -( 2 + num.size() )]
            counters[predicate] = num as int
        }

        return counters.asImmutable()
    }


    @Override
    public void addBlock(String datalogString)
    {
        executeCommand("bloxbatch -db $workspace -addBlock '$datalogString' ")
    }


    @Override
    public void addBlock(File file)
    {
        if (!file.exists())
            throw new IllegalArgumentException()

        executeCommand("bloxbatch -db $workspace -addBlock -file $file ")
    }


    @Override
    public void executeBlock(String blockName)
    {
        executeCommand("bloxbatch -db $workspace -execute -name '$blockName' ")
    }


    @Override
    public void execute(String datalogString)
    {
        executeCommand("bloxbatch -db $workspace -execute '$datalogString' ")
    }


    @Override
    public void execute(File file)
    {
        executeCommand("bloxbatch -db $workspace -execute -file $file ")
    }


    @Override
    public void processPredicate(String predicate, Closure outputLineProcessor)
    {
        executeCommand("bloxbatch -db $workspace -query '$predicate' ",
                       outputLineProcessor)
    }


    @Override
    public List<String> listPredicates()
    {
        List<String> predicates = new LinkedList<>();

        executeCommand("bloxbatch -db $workspace -list ") { String line ->
            predicates.add(line)
        }

        return predicates.asImmutable()
    }
}
