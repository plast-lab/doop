package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Android functionality.
 */
class AndroidTests extends Specification {

	final static String DOOP_BENCHMARKS = "DOOP_BENCHMARKS"
	static String doopBenchmarksDir
	Analysis analysis

	def setupSpec() {
		Doop.initDoopFromEnv()

		doopBenchmarksDir = System.getenv(DOOP_BENCHMARKS)
		if (!doopBenchmarksDir) {
			System.err.println("Error: environment variable ${DOOP_BENCHMARKS} not set, cannot run Android analysis tests")
		}
		assert null != doopBenchmarksDir
	}

	@Unroll
	def "Android analysis test androidterm"() {
		when:
		List args = ["-i", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm-1.0.70-71-minAPI4.apk",
					 "-a", "context-insensitive", "--Xserver-logic",
					 "--platform", "android_25_fulljars",
					 // "--heapdl-file", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm.hprof.gz",
					 "--id", "test-android-androidterm",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--generate-jimple", "--decode-apk", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>')
		varPointsTo(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/$r0', '<android component object jackpal.androidterm.RunScript>', true)
		instanceFieldPointsTo(analysis, '<android.widget.AdapterView$AdapterContextMenuInfo: android.view.View targetView>', '<jackpal.androidterm.Term: jackpal.androidterm.TermView createEmulatorView(jackpal.androidterm.emulatorview.TermSession)>/new jackpal.androidterm.TermView/0')
	}
}
