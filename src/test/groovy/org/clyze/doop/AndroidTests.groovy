package org.clyze.doop

import java.nio.file.Files
import org.clyze.analysis.Analysis
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
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive", "--Xserver-logic",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--gen-opt-directives", "--decode-apk",
					 "--thorough-fact-gen", "--sanity",
					 "--scan-native-code",
					 "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>')
		varPointsToQ(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/@this', '<android component object jackpal.androidterm.RunScript>')
		varValue(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/@this', '<android component object jackpal.androidterm.RunScript>')
		instanceFieldPointsTo(analysis, '<android.widget.AdapterView$AdapterContextMenuInfo: android.view.View targetView>', '<jackpal.androidterm.Term: jackpal.androidterm.TermView createEmulatorView(jackpal.androidterm.emulatorview.TermSession)>/new jackpal.androidterm.TermView/0')
		noSanityErrors(analysis)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Types-only Android analysis test (androidterm)"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "types-only", "--Xserver-logic",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm-types-only",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--gen-opt-directives", "--decode-apk",
					 "--thorough-fact-gen", "--sanity",
					 "--scan-native-code", "--simulate-native-returns",
					 "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void <init>()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onActivityResult(int,int,android.content.Intent)>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onCreate(android.os.Bundle)>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onDestroy()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onPause()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onStart()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onStop()>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onNewIntent(android.content.Intent)>')
		methodIsReachable(analysis, '<jackpal.androidterm.Term: void onUpdate()>')
		// Test application package.
		isApplicationPackage(analysis, 'jackpal.androidterm')
		// Test activities: Term, RemoteInterface.
		isActivity(analysis, 'jackpal.androidterm.Term')
		isActivity(analysis, 'jackpal.androidterm.RemoteInterface')
		// Test service: TermService.
		isService(analysis, 'jackpal.androidterm.TermService')
		noSanityErrors(analysis, false)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Types-only Android analysis test (Signal)"() {
		when:
		List args = ["-i", Artifacts.SIGNAL_APK,
					 "-a", "types-only", "--Xserver-logic",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-signal-types-only",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--gen-opt-directives", "--decode-apk",
					 "--thorough-fact-gen", "--sanity",
					 "--scan-native-code", "--simulate-native-returns",
					 "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		// Test application package.
		isApplicationPackage(analysis, 'org.thoughtcrime.securesms')
		// Test activity from manifest (fully qualified name).
		isActivity(analysis, 'org.thoughtcrime.securesms.WebRtcCallActivity')
		// Test activity from manifest (dot-syntax prefix).
		isActivity(analysis, 'org.thoughtcrime.securesms.CountrySelectionActivity')
		// Test launcher activity (via activity alias).
		isLauncherActivity(analysis, 'org.thoughtcrime.securesms.ConversationListActivity')
		// Test service (fully-qualified name).
		isService(analysis, 'org.thoughtcrime.securesms.service.WebRtcCallService')
		// Test service (dot-syntax prefix).
		isService(analysis, 'org.thoughtcrime.securesms.service.ApplicationMigrationServic')
		// Test content provider (dot-syntax prefix).
		isContentProvider(analysis, 'org.thoughtcrime.securesms.providers.MmsBodyProvider')
		// Test broadcast receiver (dot-syntax prefix).
		isBroadcastReceiver(analysis, 'org.thoughtcrime.securesms.service.DirectoryRefreshListener')
		// Test layout control.
		isLayoutControl(analysis, '2131296603', 'org.thoughtcrime.securesms.components.emoji.EmojiEditText')
		// Test reachability of launcher methods.
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onBackPressed()>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onCreate(android.os.Bundle,boolean)>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onCreateConversation(long,org.thoughtcrime.securesms.recipients.Recipient,int,long)>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onDestroy()>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onPreCreate()>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onResume()>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void onSwitchToArchive()>')
		methodIsReachable(analysis, '<org.thoughtcrime.securesms.ConversationListActivity: void <init>()>')
		// Test analysis sanity.
		noSanityErrors(analysis, false)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Featherweight/HeapDL Android analysis test"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--featherweight-analysis",
					 "--heapdl-file", "${doopBenchmarksDir}/android-benchmarks/jackpal.androidterm.hprof.gz",
					 "--id", "test-android-androidterm-fw-heapdl",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--thorough-fact-gen", "--sanity",
					 "--decode-apk", "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		// We only test if the logic compiles and loads the dynamic facts. The
		// sanity check cannot pass: HRPOF contains objects with unknown types.
		// noSanityErrors(analysis)
		true
	}

	// @spock.lang.Ignore
	@Unroll
	def "Custom Dex front end test"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm-dex",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--dex", "--decode-apk", "--Xstats-full", "-Ldebug",
					 "--Xdry-run"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		// We only test if the front end does not fail.
		true == true
	}
}
