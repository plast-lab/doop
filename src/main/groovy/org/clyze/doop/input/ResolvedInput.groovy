package org.clyze.doop.input

/**
 * A resolved input.
 */
class ResolvedInput implements Input {
	private final String input
	private final Set<File> files

	private ResolvedInput(String input) {
		this.input = input
		this.files = [] as Set
	}

	ResolvedInput(String input, File file) {
		this(input)
		this.files << file
	}

	ResolvedInput(String input, List<File> files) {
		this(input)
		this.files.addAll(files)
	}

	String name() { input }

	Set<File> files() { files }

	String toString() { files.toString() }
}
