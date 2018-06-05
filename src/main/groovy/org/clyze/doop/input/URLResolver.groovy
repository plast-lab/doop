package org.clyze.doop.input

import groovy.transform.TupleConstructor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.InputType

/**
 * Resolves the input as a URL.
 */
@TupleConstructor
class URLResolver implements InputResolver {

	File dir

	String name() { "url (${dir?.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		try {
			def url = new URL(input)
			if (dir) {
				def f = new File(dir, FilenameUtils.getBaseName(input) + "." + FilenameUtils.getExtension(input))
				FileUtils.copyURLToFile(url, f)
				ctx.set(input, f, inputType)
			} else {
				def f = File.createTempFile(FilenameUtils.getBaseName(input) + "_", "." + FilenameUtils.getExtension(input))
				FileUtils.copyURLToFile(url, f)
				f.deleteOnExit()
				ctx.set(input, f, inputType)
			}
		}
		catch (e) {
			throw new RuntimeException("Not a valid URL input: $input", e)
		}
	}
}
