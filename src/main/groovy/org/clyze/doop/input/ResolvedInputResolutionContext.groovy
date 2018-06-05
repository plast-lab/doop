package org.clyze.doop.input

import org.clyze.analysis.InputType
import org.clyze.doop.input.InputResolutionContext

class ResolvedInputResolutionContext implements InputResolutionContext {

	private final List<String> inputs
	private final List<String> libraries
	private final List<String> hprofs
	private final List<File> inputFiles
	private final List<File> libraryFiles
	private final List<File> heapFiles

	ResolvedInputResolutionContext(List<String> inputs, List<String> libraries, List<String> hprofs, List<File> inputFiles, List<File> libraryFiles, List<File> heapFiles) {
		this.inputs = inputs
		this.libraries = libraries
		this.hprofs = hprofs
		this.inputFiles = inputFiles
		this.libraryFiles = libraryFiles
		this.heapFiles = heapFiles
	}

	@Override
	boolean isTransitive() {
		return false
	}

	@Override
	void setTransitive(boolean transitive) {
		throw new UnsupportedOperationException()
	}

	@Override
	void add(String input, InputType inputType) {
		throw new UnsupportedOperationException()
	}

	@Override
	void add(List<String> inputs, InputType inputType) {
		throw new UnsupportedOperationException()
	}

	@Override
	void set(String input, File file, InputType inputType) {
		throw new UnsupportedOperationException()
	}

	@Override
	void set(String input, List<File> files, InputType inputType) {
		throw new UnsupportedOperationException()
	}

	@Override
	Set<File> get(String input, InputType inputType) {
		throw new UnsupportedOperationException()
	}

	@Override
	void resolve() {
		//do nothing
	}

	@Override
	List<File> getAll() {
		List<File> all = new LinkedList<>()
		all.addAll(inputFiles)
		all.addAll(libraryFiles)
		return all
	}

	@Override
	List<File> getAllLibraries() {
		return libraryFiles
	}

	@Override
	List<File> getAllInputs() {
		return inputFiles
	}

	@Override
	List<File> getAllHprofs() {
		return heapFiles
	}

	@Override
	List<String> inputs() {
		return inputs
	}

	@Override
	List<String> libraries() {
		return libraries
	}

	@Override
	List<String> hprofs() {
		return hprofs
	}
}
