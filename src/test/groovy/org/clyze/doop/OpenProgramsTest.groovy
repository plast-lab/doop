package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test open-program analysis.
 */
class OpenProgramsTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Testing support for open programs"(String profile) {
        when:
        List<String> options = ['--cache', '--open-programs', profile, '--platform', 'java_7', '--no-standard-exports']
        Analysis analysis = analyzeTest('ivy-open-programs', 'org.apache.ivy:ivy:2.3.0', options, 'context-insensitive')

        then:
        metricIsApprox(analysis, "call graph edges (INS)", 109_805)
        metricIsApprox(analysis, "reachable methods (INS)", 18_950)
        metricIsApprox(analysis, "array index points-to (INS)", 53_077)
        metricIsApprox(analysis, "instance field points-to (INS)", 389_306)
        metricIsApprox(analysis, "var points-to (INS)", 6_888_742)

	where:
	    profile << ['jackee'] // 'servlets-only']
    }

    // @spock.lang.Ignore
    @Unroll
    def "Test all open-program profiles"(String profile) {
        when:
        // Some analyses do not support full stats or server logic.
        Main.main((String[])(['-i', Artifacts.HELLO_JAR,
                              '-a', 'context-insensitive', '-Ldebug', '--dry-run',
                              '--souffle-force-recompile', '--souffle-mode', 'translated',
                              '--id', "dry-run-open-programs-${profile}", '--cache']))
        Analysis analysis = Main.analysis

        then:
        assert true

        where:
        profile << ['alfresco', 'concrete-types', 'jackee', 'servlets-only', 'spring']
    }
}
