package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

// A class that initializes Doop before running its tests. Tests
// should reuse this class, if they read Doop paths.
abstract class DoopSpec extends Specification {
    def setupSpec() {
	    Doop.initDoopWithLoggingFromEnv()
    }

    Analysis analyzeTest(String test, String input, List<String> extraArgs, String analysisName = "context-insensitive", String id = null) {
	String analysisId = id ?: "test-${test}"
	    List args = ["-i", input,
			 "-a", analysisName,
			 "--id", analysisId,
			 "-Ldebug",
			 "--Xstats-full"] + extraArgs
	    Main.main2((String[])args)
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

    protected List<String> getTestExports() {
        return [ "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl" ]
    }

    protected List<String> getSkipExports() {
        return [ "--Xstats-none", "--no-standard-exports" ]
    }
}
