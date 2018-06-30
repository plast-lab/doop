package org.clyze.doop.input

import groovy.transform.TupleConstructor
import org.clyze.analysis.InputType

@TupleConstructor
class ResolvedInputResolutionContext implements InputResolutionContext {

	List<String> inputs
	List<String> libraries
	List<String> platformFiles
	List<String> heapDLs
	List<File> allInputs
	List<File> allLibraries
	List<File> allPlatformFiles
	List<File> allHeapDLs

	String toString() {
"""
inputs = $inputs
libraries = $libraries
platformFiles = $platformFiles
heapDLs = $heapDLs
allInputs = $allInputs
allLibraries = $allLibraries
allPlatformFiles = $allPlatformFiles
allHeapDLs = $allHeapDLs"""
	}

	@Override
	boolean isTransitive() { false }

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
	List<File> getAll() { allInputs + allLibraries + allPlatformFiles + allHeapDLs }

	@Override
	List<String> inputs() { inputs }

	@Override
	List<String> libraries() { libraries }

	@Override
	List<String> platformFiles() { platformFiles }

	@Override
	List<String> heapDLs() { heapDLs }
}
