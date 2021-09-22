package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Python mode (fact generation and analysis).
 */
class PythonTest extends Specification {

	// @spock.lang.Ignore
	@Unroll
	def "Python test -- fact generation and analysis"(def mode) {
		when:
		Main.main((String[])(["-i", Artifacts.GLOVAR_PY, "-a", "context-insensitive", "--id", "glovar", "--platform", "python_2", "-Ldebug"] + mode))
		Analysis analysis = Main.analysis

		then:
		relationHasExactSize(analysis, "mainAnalysis.VarPointsTo", 4)

        where:
        mode << [[], ["--Xisolate-fact-generation"]]
	}
}
