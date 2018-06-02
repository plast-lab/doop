package org.clyze.doop.input

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.InputType

/**
 * Resolves the input as a URL.
 */
class URLResolver implements InputResolver {

	String name() { "url" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		try {
			def url = new URL(input)
			def tmpFile = File.createTempFile(FilenameUtils.getBaseName(input) + "_", "." + FilenameUtils.getExtension(input))
			FileUtils.copyURLToFile(url, tmpFile)
			tmpFile.deleteOnExit()
			ctx.set(input, tmpFile, inputType)
		}
		catch (e) {
			throw new RuntimeException("Not a valid URL input: $input", e)
		}
	}
}
