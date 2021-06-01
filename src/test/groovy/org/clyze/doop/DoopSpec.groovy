package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import static org.clyze.doop.TestUtils.*

// A class that initializes Doop before running its tests. Tests
// should reuse this class, if they read Doop paths.
abstract class DoopSpec extends Specification {
    def setupSpec() {
        Doop.initDoopWithLoggingFromEnv()
    }

    Analysis analyzeTest(String test, String input, List<String> extraArgs, String analysisName = "context-insensitive", String id = null) {
    String analysisId = id ?: "test-${test}"
        List<String> args = ["-i", input,
                             "-a", analysisName,
                             "--id", analysisId,
                             "-Ldebug"] + extraArgs
        if (!extraArgs.contains("stats"))
            args.addAll(['--stats', 'default'])
        Main.main2(args as String[])
        return Main.analysis
    }

    Analysis analyzeBuiltinTest(String test, List<String> extraArgs, String analysisName = "context-insensitive", String id = null) {
        return analyzeTest(test, testJar(test), extraArgs, analysisName, id)
    }

    String testJar(String test) {
        "tests/${test}/build/libs/${test}.jar"
    }

    protected List<String> getSanityOpts() {
        return [ "--sanity" ] + testExports
    }

    protected List<String> getSouffleInterpreter() {
        return [ "--souffle-mode", "interpreted" ]
    }

    protected List<String> getTestExports() {
        return [ "--extra-logic", "${Doop.souffleLogicPath}/addons/testing/test-exports.dl" ]
    }
}
