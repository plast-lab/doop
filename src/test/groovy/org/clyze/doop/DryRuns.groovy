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

	Analysis analysis
	List analysisNames

	def setupSpec() {
		Doop.initDoopFromEnv()
	}

	@Unroll
	def "Test analysis compilation"(String analysisName) {
		when:
		// Some analyses do not support full stats.
		String stats = ((analysisName == "micro") || (analysisName == "sound-may-point-to")) ? "--Xstats-none" : "--Xstats-full"
		Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/Demo-benchmarks/test-resources/006-hello-world-1.2.jar",
							 "-a", analysisName,
							 "--id", "dry-run-${analysisName}",
							 "--Xdry-run", "--Xforce-recompile",
							 stats])
		analysis = Main.analysis

        then:
        execExists(analysis)

        where:
        analysisName << DoopAnalysisFamily.analysesSouffle()
    }
}
