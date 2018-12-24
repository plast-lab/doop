package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysisFamily
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test that all analyses compile.
 */
class DryRuns extends Specification {

	def setupSpec() {
		Doop.initDoopFromEnv()
	}

	@Unroll
	def "Test analysis compilation"(String analysisName) {
		when:
		// Some analyses do not support full stats.
		String stats = ((analysisName == "micro") || (analysisName == "sound-may-point-to")) ? "--Xstats-none" : "--Xstats-full"
		Main.main((String[])["-i", Artifacts.HELLO_JAR,
							 "-a", analysisName,
							 "--id", "dry-run-${analysisName}",
							 "--Xdry-run", "--souffle-force-recompile",
							 stats])
		Analysis analysis = Main.analysis

        then:
        execExists(analysis)

        where:
        analysisName << DoopAnalysisFamily.analysesSouffle()
    }
}
