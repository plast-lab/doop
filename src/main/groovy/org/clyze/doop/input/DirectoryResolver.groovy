package org.clyze.doop.input

import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

/**
 * Resolves the input as a directory that contains jar files.
 */
class DirectoryResolver implements InputResolver {

	String name() { "directory" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		def dir = FileOps.findDirOrThrow(input, "Not a directory input: $input")
		def filter = FileOps.extensionFilter("jar")

		def filesInDir = dir.listFiles(filter).toList()
		ctx.set(input, filesInDir.sort { it.toString() }, inputType)
	}
}
