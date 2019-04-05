package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

/**
 * Test the combination of open-program analysis and HeapDL.
 */
class OpenProgramsAndHeapDL extends DoopBenchmark {
	// @spock.lang.Ignore
	@Unroll
	def "HeapDL/Open-Programs Android analysis test"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--open-programs", "concrete-types",
					 "--heapdl-file", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm.hprof.gz",
					 "--id", "test-android-androidterm-open-programs-heapdl",
					 "--Xdry-run"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		// We only test that the logic compiles.
		true
	}
}
