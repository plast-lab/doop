package org.clyze.doop.input

import groovy.transform.TupleConstructor
import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

/**
 * Resolves the input as a local file.
 */
@TupleConstructor
class FileResolver implements InputResolver {

	File dir

	String name() { "file (${dir?.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		def inputFile = dir ? new File(dir, input) : new File(input)
		def file = FileOps.findFileOrThrow(inputFile, "Not a file input: $inputFile.name")
		ctx.set(input, file, inputType)
	}
}
