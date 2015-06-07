package doop.blox;

/**
 * This interface models a Connector class to a LogicBlox workspace.
 *
 * It defines a number of methods that closely resemble the existing
 * bloxbatch commands.
 */
interface WorkspaceConnector
{
    /**
     * Create a new database.
     *
     * If a database already exists at that location, an error will be
     * reported unless the -overwrite option is used.
     *
     * @param shouldOverwrite if true and the workspace already
     *    exists, delete it
     */
    public void create(boolean shouldOverwrite)


    /**
     * Run the specified datalog query and process the query results
     * using {@code outputLineProcessor}.
     *
     * @param datalogString  the query to run
     * @param outputLineProcessor a single-argument closure that will
     *    process each line (as a String) of the query output
     */
    public void processQuery(String datalogString, Closure outputLineProcessor)


    /**
     * Run the specified datalog query and process the query results
     * indicated by the {@code printOpt} argument, using {@code
     * outputLineProcessor}.
     *
     * @param datalogString  the query to run
     * @param printOpt the predicate whose contents will be processed
     *    as the query results
     * @param outputLineProcessor a single-argument closure that will
     *    process each line (as a String) of the query output
     */
    public void processQuery(String datalogString, String printOpt, Closure outputLineProcessor)


    /**
     * Get the number of populated facts in the listed predicates.
     *
     * @param predicates  the predicates to be counted
     *
     * @return A map from predicate names to their counters.
     */
    public Map<String,Integer> popCount(String... predicates)


    /**
     * Add the specified datalog rules to the database's set of
     * installed rules.
     *
     * @param datalogString  the datalog rules to be added
     */
    public void addBlock(String datalogString)


    /**
     * Add the given Datalog file to the database's set of installed
     * rules.
     *
     * @param file  the file to be added
     */
    public void addBlock(File file)


    /**
     * Retrieve a named block from the database and execute the logic
     * in that block to modify the database. The logic will be
     * executed once.
     *
     * @param blockName  the block to be executed
     */
    public void executeBlock(String blockName)


    /**
     * Execute the specified datalog rules to modify data in a
     * database.  These rules are not installed into the database, but
     * are forgotten after being executed.
     *
     * @param datalogString  the logic to be executed
     */
    public void execute(String datalogString)


    /**
     * Execute datalog rules contained in the given file to modify
     * data in a database.  These rules are not installed into the
     * database, but are forgotten after being executed.
     *
     * @param file  the file containing the logic to be executed
     */
    public void execute(File file)


    /**
     * Process the contents of the specified datalog predicate using
     * {@code outputLineProcessor}.
     *
     * @param predicate  the predicate to process
     * @param outputLineProcessor a single-argument closure that will
     *    process each line (as a String) of the predicate contents
     */
    public void processPredicate(String predicate, Closure outputLineProcessor)


    /**
     * List the full names of all predicates of the workspace.
     *
     * @return a list of predicate names
     */
    public List<String> listPredicates()
}
