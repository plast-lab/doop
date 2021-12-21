package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test programs from the server-analysis-tests repo.
 */
abstract class ServerAnalysisTests extends DoopSpec {

	final static String SERVER_ANALYSIS_TESTS_DIR = "SERVER_ANALYSIS_TESTS_DIR"
	static String serverAnalysisTestsDir

	def setupSpec() {
		serverAnalysisTestsDir = System.getenv(SERVER_ANALYSIS_TESTS_DIR)
		if (!serverAnalysisTestsDir) {
			System.err.println("ERROR: Environment variable ${SERVER_ANALYSIS_TESTS_DIR} not set, cannot run server analysis tests")
		}
		assert null != serverAnalysisTestsDir
	}

	Analysis analyzeTest(String test, List<String> extraArgs, String analysisName = "context-insensitive", String id = null) {
		return super.analyzeTest(test, "${serverAnalysisTestsDir}/${test}/build/libs/${test}.jar", extraArgs, analysisName, id)
	}
}
