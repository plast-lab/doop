package org.clyze.doop

import org.clyze.analysis.Analysis
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
        List<String> args = ['-i', Artifacts.HELLO_JAR,
                             '-a', analysisName,
                             '--id', "dry-run-${analysisName}", '--cache',
                             '--dry-run', '--souffle-mode', 'translated', '-Ldebug']
        // Some analyses do not support full stats or server logic.
        switch (analysisName) {
            case 'micro':
            case 'sound-may-point-to':
                args.addAll(['--stats', 'none' ])
                break
            case 'types-only':
                args.addAll(['--stats', 'full', '--server-logic', '--disable-points-to' ])
                break
            default:
                args.addAll(['--stats', 'full', '--server-logic' ])
        }
        Main.main(args as String[])
        Analysis analysis = Main.analysis

        then:
        cppExists(analysis)

        where:
        // Omit analyses tested elsewhere or having problems with dry runs.
        analysisName << DoopAnalysisFamily.analysesSouffle().findAll {
            it != 'fully-guided-context-sensitive'
        }
    }
}
