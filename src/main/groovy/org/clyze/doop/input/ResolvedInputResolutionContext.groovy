package org.clyze.doop.input

import groovy.transform.TupleConstructor
import org.clyze.analysis.InputType

@TupleConstructor
class ResolvedInputResolutionContext implements InputResolutionContext {

	List<String> inputs
	List<String> libraries
	List<String> platformFilenames
	List<String> hprofs
	List<File> allInputs
	List<File> allLibraries
	List<File> allPlatformFiles
	List<File> allHprofs

	String toString() {
		return "Resolved input resolution context:\n" +
			"inputs = ${inputs}\n" +
			"libraries = ${libraries}\n" +
			"platformFilenames = ${platformFilenames}\n" +
			"hprofs = ${hprofs}\n" +
			"allInputs = ${allInputs}\n" +
			"allLibraries = ${allLibraries}\n" +
			"allPlatformFiles = ${allPlatformFiles}\n" +
			"allHprofs = ${allHprofs}\n"
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
	List<File> getAll() { allInputs + allLibraries + allPlatformFiles + allHprofs }

	@Override
	List<String> inputs() { inputs }

	@Override
	List<String> libraries() { libraries }

	@Override
	List<String> platformFiles() { platformFilenames }

	@Override
	List<String> hprofs() { hprofs }
}
