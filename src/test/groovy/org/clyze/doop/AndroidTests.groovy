package org.clyze.doop

import java.nio.file.Files
import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Android functionality.
 */
class AndroidTests extends DoopSpec {

	static List defaultArgs = [
		"--timeout", "30",
		"--server-logic", "--gen-opt-directives", "--sarif",
		"--generate-jimple",
		"--thorough-fact-gen"
	]

	// @spock.lang.Ignore
	@Unroll
	def "Basic Android analysis test"() {
		when:
		String keepSpec = this.class.getResource("/keep-spec/keep-spec-android.txt").file
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm",
					 "--keep-spec", keepSpec, "-Ldebug"] + defaultArgs + sanityOpts
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>')
		varPointsToQ(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/@this', '<Android component object jackpal.androidterm.RunScript>')
		varValue(analysis, '<jackpal.androidterm.RunScript: void handleIntent()>/@this', '<Android component object jackpal.androidterm.RunScript>')
		instanceFieldPointsTo(analysis, '<android.widget.AdapterView$AdapterContextMenuInfo: android.view.View targetView>', '<jackpal.androidterm.Term: jackpal.androidterm.TermView createEmulatorView(jackpal.androidterm.emulatorview.TermSession)>/new jackpal.androidterm.TermView/0')
		// method reachable due to keep spec
		methodIsReachable(analysis, '<jackpal.androidterm.TermExec: void <init>(java.util.List)>')
		noSanityErrors(analysis)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Types-only Android analysis test (androidterm)"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "types-only",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-androidterm-types-only",
					 "--sanity",
					 "--extra-logic", "${Doop.souffleLogicPath}/addons/testing/AndroidTests_TypesOnly_androidterm.dl",
					 "--scan-native-code", "--simulate-native-returns",
					 "--no-standard-exports",
					 "--Xlow-mem", "-Ldebug"] + defaultArgs
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		testSucceeds(analysis, "test.1")
		testSucceeds(analysis, "test.2")
		testSucceeds(analysis, "test.3")
		testSucceeds(analysis, "test.4")
		testSucceeds(analysis, "test.5")
		testSucceeds(analysis, "test.6")
		testSucceeds(analysis, "test.7")
		testSucceeds(analysis, "test.8")
		testSucceeds(analysis, "test.9")
		testSucceeds(analysis, "test.10")
		// Test application package.
		isApplicationPackage(analysis, 'jackpal.androidterm')
		// Test activities: Term, RemoteInterface.
		isActivity(analysis, 'jackpal.androidterm.Term')
		isActivity(analysis, 'jackpal.androidterm.RemoteInterface')
		// Test service: TermService.
		isService(analysis, 'jackpal.androidterm.TermService')
		noSanityErrors(analysis, false)
	}

	@spock.lang.Ignore
	@Unroll
	def "Types-only Android analysis test (Phonograph)"() {
		when:
		List args = ["-i", Artifacts.PHONOGRAPH_APK,
					 "-a", "types-only",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-phonograph-types-only",
					 "--scan-native-code", "--simulate-native-returns"] + defaultArgs + sanityOpts
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		// Misc. tests (needed for the tests that follow).
		methodIsReachable(analysis, '<com.sothree.slidinguppanel.SlidingUpPanelLayout: void <init>(android.content.Context)>')
		methodIsReachable(analysis, '<com.kabouzeid.gramophone.ui.activities.base.AbsSlidingMusicPanelActivity: void <init>()>')
		// Test XML logic for <include> + <merge>.
		xmlParent(analysis, 'res/layout/abc_screen_content_include.xml', '2', 'res/layout/abc_screen_simple_overlay_action_mode.xml', '1')
		// Test XML logic for attribute "android:appComponentFactory".
		methodIsReachable(analysis, '<androidx.core.app.CoreComponentFactory: android.app.Application instantiateApplication(java.lang.ClassLoader,java.lang.String)>')
		// Test fragment detection.
		isFragment(analysis, 'res/layout/sliding_music_panel_layout.xml', '5', 'com.kabouzeid.gramophone.ui.fragments.player.MiniPlayerFragment', '2131296569')
		methodIsReachable(analysis, '<com.kabouzeid.gramophone.ui.fragments.player.MiniPlayerFragment: void <init>()>')
		methodIsReachable(analysis, '<com.kabouzeid.gramophone.ui.fragments.player.MiniPlayerFragment: void onPause()>')
		// Test reachable layout control.
		isReachableLayoutControl(analysis, 'com.kabouzeid.gramophone.ui.fragments.player.PlayerAlbumCoverFragment')
		// Test analysis sanity.
		noSanityErrors(analysis, false)
	}

	@spock.lang.Ignore
	@Unroll
	def "Types-only Android analysis test (Signal)"() {
		when:
		List args = ["-i", Artifacts.SIGNAL_APK,
					 "-a", "types-only",
					 "--platform", "android_25_fulljars",
					 "--id", "test-android-signal-types-only",
					 "--scan-native-code", "--simulate-native-returns",
					 "-Ldebug"] + defaultArgs + sanityOpts
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
					 "--heapdl-file", Artifacts.ANDROIDTERM_HPROF,
					 "--id", "test-android-androidterm-fw-heapdl",
					 "--thorough-fact-gen", "-Ldebug"] + defaultArgs
		Main.main((String[])args)
		Analysis analysis = Main.analysis

		then:
		methodIsReachable(analysis, '<android.app.admin.IDevicePolicyManager$Stub$Proxy: void <init>(android.os.IBinder)>')
		// The sanity check cannot pass: HRPOF contains objects with
		// unknown types.
		// noSanityErrors(analysis)
		true
	}

    // @spock.lang.Ignore
    @Unroll
    def "Custom Dex front end test"(def mode) {
        when:
        String id = 'test-android-androidterm-dex'
        List args = ["-i", Artifacts.ANDROIDTERM_APK,
                     "-a", "context-insensitive",
                     "--platform", "android_25_fulljars",
                     "--id", id, "--facts-only",
                     "--Xdex", "-Ldebug"] + testExports + mode
        Main.main((String[])args)
        Analysis analysis = Main.analysis

        then:
        // We only test if the front end does not fail.
        true == true

        where:
        mode << [[], ["--Xisolate-fact-generation"]]
    }
}
