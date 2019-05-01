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

	// If true, the resolver assumes a common 'tmp' pool of downloaded files
	// that (a) have unique names and (b) are to be deleted on Doop exit.
	boolean tmpPool = false

	String name() { "url (${dir?.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		try {
			def url = new URL(input)
			if (!dir) {
				String msg = "Error: no 'dir' set in URL resolver."
				println msg
				throw new RuntimeException(msg)
			}

			def f
			String base = FilenameUtils.getBaseName(input)
			String ext = "." + FilenameUtils.getExtension(input)
			if (tmpPool) {
				f = File.createTempFile(base + "_", ext, dir)
				f.deleteOnExit()
			} else {
				f = new File(dir, base + ext)
			}
			FileUtils.copyURLToFile(url, f)
			ctx.set(input, f, inputType)
		}
		catch (e) {
			throw new RuntimeException("Not a valid URL input: $input", e)
		}
	}
}
