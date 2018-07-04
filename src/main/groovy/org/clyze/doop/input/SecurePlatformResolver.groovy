package org.clyze.doop.input

import groovy.transform.TupleConstructor
import org.clyze.analysis.InputType
import org.clyze.utils.FileOps

/**
 * Resolves the input as a platform file.
 * Apply security checks for absolute paths.
 */
@TupleConstructor
class SecurePlatformResolver implements InputResolver {

	File platformsLibDir

	String name() { "secure platform (${platformsLibDir.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		if (inputType != InputType.PLATFORM)
			throw new RuntimeException("Non-valid resolver for $inputType")

		// Allow only for absolute paths inside the platformsDir, with no references to parent dir
		if (!input.startsWith(platformsLibDir.absolutePath) || input.contains(".."))
			throw new RuntimeException("Not a valid platform file: $input")

		def file = FileOps.findFileOrThrow(new File(input), "Not a file input: $input")
		ctx.set(input, file, inputType)
	}
}
