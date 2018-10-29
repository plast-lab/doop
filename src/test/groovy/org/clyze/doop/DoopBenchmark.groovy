package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification

/**
 * Tests that come from the Doop benchmarks repo.
 */
abstract class DoopBenchmark extends Specification {
	final static String DOOP_BENCHMARKS = "DOOP_BENCHMARKS"
	static String doopBenchmarksDir
	Analysis analysis

	def setupSpec() {
		Doop.initDoopFromEnv()

		doopBenchmarksDir = System.getenv(DOOP_BENCHMARKS)
		if (!doopBenchmarksDir) {
			System.err.println("Error: environment variable ${DOOP_BENCHMARKS} not set, cannot run tests")
		}
		assert null != doopBenchmarksDir
	}
}
