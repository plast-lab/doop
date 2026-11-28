package org.clyze.doop.input

import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.InputType
import org.clyze.input.InputResolutionContext
import org.clyze.input.InputResolver

/**
 * Resolves a URL input by downloading it. The download keeps the URL's file
 * name (in a fresh directory, so concurrent analyses do not collide), because
 * facts name inputs by file name: the library's URLResolver downloads to a
 * temporary file with a random suffix, which made the facts of a URL input
 * differ on every run.
 */
@CompileStatic
class URLInputResolver implements InputResolver {

	final File dir

	URLInputResolver(File dir) { this.dir = dir }

	String name() { "url (${dir.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		URL url
		try {
			url = new URL(input)
		} catch (MalformedURLException ignored) {
			throw new RuntimeException("Not a valid URL input: ${input}")
		}
		dir.mkdirs()
		File downloadDir = Files.createTempDirectory(dir.toPath(), "url-").toFile()
		String name = FilenameUtils.getName(url.path) ?: "input"
		File file = new File(downloadDir, name)
		File partial = new File(downloadDir, name + ".part")
		FileUtils.copyURLToFile(url, partial)
		Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
		downloadDir.deleteOnExit()
		file.deleteOnExit()
		ctx.set(input, file, inputType)
	}
}
