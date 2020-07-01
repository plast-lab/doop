package org.clyze.doop.input

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.clyze.analysis.InputType
import org.clyze.input.InputResolutionContext
import org.clyze.input.InputResolver
import org.clyze.utils.FileOps

/**
 * Resolves the input as a platform file, recursing into sub-directories.
 * Apply security checks for absolute paths.
 */
@CompileStatic
@TupleConstructor
class RecursiveBenchmarksResolver implements InputResolver {

	File benchmarkDir

	String name() { "rbenchmarks (${benchmarkDir.absolutePath})" }

	void resolve(String input, InputResolutionContext ctx, InputType inputType) {
		if (input.contains(".."))
			throw new RuntimeException("Not a valid platform file: $input")

		def finder = new FileNameFinder()
		def res = ["android-benchmarks", "dacapo-2006", "dacapo-bach"].collect {
			try {
				def names = finder.getFileNames(new File(benchmarkDir, it) as String, "**/$input")
				names ? FileOps.findFileOrThrow(names[0], "") : null
			} catch (all) {
				null
			}
		}.grep()
		if (res)
			ctx.set(input, res.first(), inputType)
		else
			throw new RuntimeException("Not a valid benchmark input: $input")
	}
}
