package org.clyze.doop.input

import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

/**
 * Resolves the input as a local file.
 */
class FileResolver implements InputResolver {

	String name() { "file" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		def file = FileOps.findFileOrThrow(input, "Not a file input: $input")
		ctx.set(input, file, inputType)
	}
}
