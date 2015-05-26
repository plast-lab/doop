package doop.input

/**
 * The input resolution mechanism.
 * Resolves inputs (given as strings) to a set of files.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 24/3/2015
 */
interface InputResolutionContext {

    /**
     * Checks whether transitive dependencies are supported.
     */
    boolean isTransitive()

    /**
     * Sets the support of transitive dependencies.
     */
    void setTransitive(boolean transitive)

    /**
     * Adds the given input for resolution.
     */
    void add(String input)

    /**
     * Adds the given list of inputs for resolution.
     */
    void add(List<String> inputs)

    /**
     * Sets the file that corresponds to the given input.
     * @param input - the input as a string
     * @param file - the file it corresponds to
     */
    void set(String input, File file)

    /**
     * Sets the files that correspond to the given input.
     * @param input - the input as a string
     * @param files - the files it corresponds to
     */
    void set(String input, List<File> files)

    /**
     * Gets the file(s) that correspond to the given input.
     * @param input - the input as a string
     * @return the corresponding file(s)
     */
    Set<File> get(String input)

    /**
     * Resolves the inputs to their corresponding files.
     */
    void resolve()

    /**
     * Gets the set of files that correspond to the inputs of this context.
     * The set is returned as a list for convenience.
     * If an input is found to be unresolved --it has not file(s)-- an exception is thrown.
     */
    List<File> getAll()

    /**
     * Returns the inputs of this context.
     */
    Set<String> inputs()
}