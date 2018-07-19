package org.clyze.doop.input

import org.clyze.analysis.InputType

/**
 * The input resolution mechanism.
 * Resolves inputFiles (given as strings) to a set of files.
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
	void add(String input, InputType inputType)

	/**
	 * Adds the given list of inputFiles for resolution.
	 */
	void add(List<String> inputs, InputType inputType)

	/**
	 * Sets the file that corresponds to the given input.
	 * @param input - the input as a string
	 * @param file - the file it corresponds to
	 */
	void set(String input, File file, InputType inputType)

	/**
	 * Sets the files that correspond to the given input.
	 * @param input - the input as a string
	 * @param files - the files it corresponds to
	 */
	void set(String input, List<File> files, InputType inputType)

	/**
	 * Gets the file(s) that correspond to the given input.
	 * @param input - the input as a string
	 * @return the corresponding file(s)
	 */
	Set<File> get(String input, InputType inputType)

	/**
	 * Resolves the inputFiles to their corresponding files.
	 * If an input is unresolved --it has no file(s)-- an exception is thrown.
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
	 * Returns all the platformFiles of this context.
	 */
	List<String> platformFiles()

	/**
	 * Returns all the heapFiles of this context.
	 */
	List<String> heapDLs()

	/**
	 * Get the setInput of files that correspond to the inputFiles of this context.
	 * If an input is found to be unresolved --it has no file(s)-- an exception is thrown.
	 */
	List<File> getAllInputs()

	List<File> getAllLibraries()

	List<File> getAllPlatformFiles()

	List<File> getAllHeapDLs()

	List<File> getAll()
}
