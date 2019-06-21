package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysisFamily
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test that all analyses compile.
 */
class DryRuns extends DoopSpec {

	// @spock.lang.Ignore
	@Unroll
	def "Test analysis compilation"(String analysisName) {
		when:
		// Some analyses do not support full stats or server logic.
		List<String> extraOpts
		switch (analysisName) {
		case 'micro':
		case 'sound-may-point-to':
			extraOpts = ["--Xstats-none" ]
			break
		case 'types-only':
			extraOpts = ['--Xstats-full', '--Xserver-logic', '--disable-points-to' ]
			break
		default:
			extraOpts = ["--Xstats-full", "--Xserver-logic" ]
		}
		Main.main((String[])(["-i", Artifacts.HELLO_JAR,
							  "-a", analysisName,
							  "--id", "dry-run-${analysisName}", "--cache",
							  "--Xdry-run", "--souffle-force-recompile"] +
							  extraOpts))
		Analysis analysis = Main.analysis

        then:
        execExists(analysis)

        where:
        // Omit analyses tested elsewhere or having problems with dry runs.
        analysisName << DoopAnalysisFamily.analysesSouffle().findAll {
            it != 'fully-guided-context-sensitive'
        }
    }
}
