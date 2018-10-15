package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test programs from the server-analysis-tests repo.
 */
abstract class ServerAnalysisTests extends Specification {

	final static String SERVER_ANALYSIS_TESTS_DIR = "SERVER_ANALYSIS_TESTS_DIR"
	static String serverAnalysisTestsDir
	Analysis analysis

	def setupSpec() {
		Doop.initDoopFromEnv()

		serverAnalysisTestsDir = System.getenv(SERVER_ANALYSIS_TESTS_DIR)
		if (!serverAnalysisTestsDir) {
			System.err.println("Error: environment variable ${SERVER_ANALYSIS_TESTS_DIR} not set, cannot run server analysis tests")
		}
		assert null != serverAnalysisTestsDir
	}

	String analyzeTest(String test, List<String> extraArgs) {
		List args = ["-i", "${serverAnalysisTestsDir}/${test}/build/libs/${test}.jar",
					 "-a", "context-insensitive", // "--Xserver-logic",
					 "--id", "test-${test}", "--generate-jimple",
					 "--Xstats-full"] + extraArgs
		Main.main2((String[])args)
		analysis = Main.analysis
	}
}
