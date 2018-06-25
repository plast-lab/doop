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
			"android_22_fulljars"   : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar"],
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
			"android_22_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar"],
			"android_23_stubs"      : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar"],
			"android_24_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			"android_25_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			"android_26_stubs"      : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			// Android-Robolectric
			"android_26_robolectric": ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
			                           "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
			//Python
			"python"                : [],
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
