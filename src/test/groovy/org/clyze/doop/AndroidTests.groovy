package org.clyze.doop

import java.nio.file.Files
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Android functionality.
 */
class AndroidTests extends DoopBenchmark {

	// @spock.lang.Ignore
	@Unroll
	def "Basic Android analysis test"() {
		when:
		List args = ["-i", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk",
					 "-a", "context-insensitive", "--Xserver-logic",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--gen-opt-directives", "--decode-apk", "--thorough-fact-gen",
					 "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>')
		varPointsToQ(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/$r0', '<android component object jackpal.androidterm.RunScript>')
		varValue(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/$r0', '<android component object jackpal.androidterm.RunScript>')
		instanceFieldPointsTo(analysis, '<android.widget.AdapterView$AdapterContextMenuInfo: android.view.View targetView>', '<jackpal.androidterm.Term: jackpal.androidterm.TermView createEmulatorView(jackpal.androidterm.emulatorview.TermSession)>/new jackpal.androidterm.TermView/0')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Featherweight/HeapDL Android analysis test"() {
		when:
		List args = ["-i", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk",
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--featherweight-analysis",
					 "--heapdl-file", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm.hprof.gz",
					 "--id", "test-android-androidterm-fw-heapdl",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--decode-apk", "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		analysis = Main.analysis

		then:
		// We only test if the logic compiles and loads the dynamic facts.
		true == true
	}

	// @spock.lang.Ignore
	@Unroll
	def "Custom Dex front end test"() {
		when:
		String tmpDir = Files.createTempDirectory("dex-test-facts").toString()
		List args = ["-i", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk",
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm-dex",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--dex", "--decode-apk", "--Xstats-full", "-Ldebug",
					 "--Xstop-at-facts", tmpDir]
		Main.main((String[])args)
		analysis = Main.analysis

		then:
		// We only test if the front end does not fail.
		true == true
	}
}
