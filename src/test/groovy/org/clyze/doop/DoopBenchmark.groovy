package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification

/**
 * Tests that come from the Doop benchmarks repository should extend this class
 * so that they can find the path to the local copy of the repository.
 */
abstract class DoopBenchmark extends Specification {
	final static String DOOP_BENCHMARKS = "DOOP_BENCHMARKS"
	static String doopBenchmarksDir

	def setupSpec() {
		Doop.initDoopFromEnv()

		doopBenchmarksDir = System.getenv(DOOP_BENCHMARKS)
		if (!doopBenchmarksDir) {
			System.err.println("Warning: environment variable ${DOOP_BENCHMARKS} not set.")
		}
	}
}
