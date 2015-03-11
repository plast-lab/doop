package doop.util.process;

/**
 * Thrown by methods that call another process, which is required to
 * succeed (and thus expected to return a zero exit code).
 */
class CalledProcessException extends RuntimeException
{
    private final String commandLine;
    private final int returnCode;

    public CalledProcessException(int returnCode, String commandLine)
    {
        super(
            String.format(
                "Command '%s' returned non-zero exit status %d",
                commandLine, returnCode)
            );


        // Store command that failed and its return code for later
        // retrieval
        this.commandLine = commandLine;
        this.returnCode = returnCode;

        // Sanity check
        if (returnCode == 0)
            throw new IllegalArgumentException("Return code cannot be zero");
    }

    public int getReturnCode() {
        return returnCode;
    }

    public String getCommandLine() {
        return commandLine;
    }
}
