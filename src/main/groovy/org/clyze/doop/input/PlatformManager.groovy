package org.clyze.doop.input

import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j

@Log4j
@TupleConstructor
class PlatformManager {

	static final String ARTIFACTORY_PLATFORMS_URL = "http://centauri.di.uoa.gr:8081/artifactory/Platforms"

	static final Map<String, Set<String>> ARTIFACTS_FOR_PLATFORM = [
			// JDKs
			"java_3"                : ["rt.jar"],
			"java_4"                : ["rt.jar", "jce.jar", "jsse.jar"],
			"java_5"                : ["rt.jar", "jce.jar", "jsse.jar"],
			"java_6"                : ["rt.jar", "jce.jar", "jsse.jar"],
			"java_7"                : ["rt.jar", "jce.jar", "jsse.jar", "tools.jar"],
			"java_7_debug"          : ["rt.jar", "jce.jar", "jsse.jar", "tools.jar"],
			"java_8"                : ["rt.jar", "jce.jar", "jsse.jar"],
			"java_8_debug"          : ["rt.jar", "jce.jar", "jsse.jar"],
			"java_8_mini"           : ["rt.jar", "jce.jar", "jsse.jar"],
			// Android compiled from sources
			"android_22_fulljars"   : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_25_fulljars"   : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar"],
			// Android API stubs (from the SDK)
			"android_7_stubs"       : ["android.jar", "data/layoutlib.jar"],
			"android_15_stubs"      : ["android.jar", "data/layoutlib.jar"],
			"android_16_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_17_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_18_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_19_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_20_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_21_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_22_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
			"android_23_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar"],
			"android_24_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			"android_25_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			"android_26_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			// Android Dalvik equivalent
			"android_25_apks"		: [ "android_accessibilityservice.apk",
										"android_accounts.apk",
										"android_animation.apk",
										"android_annotation.apk",
										"android_app.apk",
										"android_appwidget.apk",
										"android_bluetooth.apk",
										"android_content.apk",
										"android_database.apk",
										"android_ddm.apk",
										"android_drm.apk",
										"android_filterfw.apk",
										"android_filterpacks.apk",
										"android_gesture.apk",
										"android_graphics.apk",
										"android_hardware.apk",
										"android_hidl.apk",
										"android_icu.apk",
										"android_inputmethodservice.apk",
										"android_location.apk",
										"android_media.apk",
										"android_mtp.apk",
										"android_net.apk",
										"android_nfc.apk",
										"android_opengl.apk",
										"android_os.apk",
										"android_permissionpresenterservice.apk",
										"android_preference.apk",
										"android_print.apk",
										"android_printservice.apk",
										"android_provider.apk",
										"android_renderscript.apk",
										"android_sax.apk",
										"android_security.apk",
										"android_service.apk",
										"android_speech.apk",
										"android_system.apk",
										"android_telecom.apk",
										"android_telephony.apk",
										"android_text.apk",
										"android_transition.apk",
										"android_util.apk",
										"android_view.apk",
										"android_webkit.apk",
										"android_widget.apk",
										"com.apk",
										"dalvik.apk",
										"java.apk",
										"javax.apk",
										"jdk.apk",
										"libcore.apk",
										"org.apk",
										"sun.apk"
									  ],
			// Android-Robolectric
			"android_26_robolectric": ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			// Python
			"python_2"              : [],
	]

	String platformsLib

	List<String> find(String platform) {
		if (!platformsLib)
			platformsLib = ARTIFACTORY_PLATFORMS_URL

		def (platformKind, version, variant) = platform.split("_").toList()
		switch (platformKind) {
			case "java":
				def platformPath = "${platformsLib}/JREs/jre1.${variant ? "${version}_$variant" : version}/lib"
				return find0(platform, platformPath)
			case "android":
				def platformPath = "${platformsLib}/Android/${variant}/Android/Sdk/platforms/android-${version}"
				def files = find0(platform, platformPath)
				if (variant == "robolectric") {
					log.info "Using Robolectric with Java 8"
					platformPath = "${platformsLib}/JREs/jre1.8/lib"
					files += find0("java_8", platformPath)
				}
				return files
		}
		return []
	}

	static final List<String> find0(String platform, String path) {
		def artifacts = ARTIFACTS_FOR_PLATFORM[platform]
		if (!artifacts)
			throw new RuntimeException("Invalid platform: $platform")
		artifacts.collect { "$path/$it" }
	}
}
