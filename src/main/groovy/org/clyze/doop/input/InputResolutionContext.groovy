package org.clyze.doop.input

/**
 * The input resolution mechanism.
 * Resolves inputFiles (given as strings) to a setInput of files.
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
    void add(String input, boolean isLib)

    /**
     * Adds the given list of inputFiles for resolution.
     */
    void add(List<String> inputs, boolean isLib)

    /**
     * Sets the file that corresponds to the given input.
     * @param input - the input as a string
     * @param file - the file it corresponds to
     */
    void set(String input, File file, boolean isLib)

    /**
     * Sets the files that correspond to the given input.
     * @param input - the input as a string
     * @param files - the files it corresponds to
     */
    void set(String input, List<File> files, boolean isLib)

    /**
     * Gets the file(s) that correspond to the given input.
     * @param input - the input as a string
     * @return the corresponding file(s)
     */
    Set<File> get(String input, boolean isLib)

    /**
     * Resolves the inputFiles to their corresponding files.
     * If an input is unresolved --it has not file(s)-- an exception is thrown.
     */
    void resolve()

    /**
     * Returns the inputFiles of this context.
     */
    List<String> inputs()

    /**
     * Returns all the libraryFiles of this context.
     */
    List<String> libraries()

    /**
     * Gets the setInput of files that correspond to the inputFiles of this context.
     * If an input is found to be unresolved --it has no file(s)-- an exception is thrown.
     */
    List<File> getAllInputs()

    /**
     * Gets the setInput of files that correspond to the inputFiles of this context.
     * If an input is found to be unresolved --it has no file(s)-- an exception is thrown.
     */
    List<File> getAllLibraries()

    List<File> getAll()

}
