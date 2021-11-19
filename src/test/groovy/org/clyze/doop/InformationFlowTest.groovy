package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysisFamily
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test information flow (P/Taint).
 */
class InformationFlowTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Information flow (P/Taint) test"() {
        when:
        List args = ["-i", Artifacts.ANDROIDTERM_APK,
                     "-a", "context-insensitive", "--server-logic",
                     "--platform", "android_25_fulljars",
                     "--information-flow", "android", "--sarif",
                     "--information-flow-extra-controls", "7878787878,android.widget.EditText,2131296798",
                     "--id", "test-android-androidterm-information-flow",
                     "--generate-jimple", "-Ldebug"] + testExports
        Main.main((String[])args)
        Analysis analysis = Main.analysis

        then:
        isSensitiveLayoutControl(analysis, '7878787878', 'android.widget.EditText')
        relationHasApproxSize(analysis, "AppTaintedVar", 2663)
        relationHasApproxSize(analysis, "AppTaintedVarPointsTo", 82178)
    }

    // @spock.lang.Ignore
    @Unroll
    def "Test all information flow profiles"(String profile) {
        when:
        // Some profiles may not support full stats or server logic.
        Main.main((String[])(['-i', Artifacts.HELLO_JAR,
                              '-a', 'context-insensitive', '-Ldebug', '--dry-run',
                              '--souffle-mode', 'translated',
                              '--id', "dry-run-infoflow-${profile}", '--cache']))
        Analysis analysis = Main.analysis

        then:
        assert true

        where:
        profile << DoopAnalysisFamily.informationFlowPlatforms(null, Doop.souffleLogicPath)
    }
}
