package org.clyze.doop.core

import org.clyze.analysis.AnalysisFamily
import org.clyze.analysis.AnalysisOption
import org.clyze.analysis.BooleanAnalysisOption
import org.clyze.analysis.IntegerAnalysisOption

import static DoopAnalysis.INFORMATION_FLOW_SUFFIX
import static org.apache.commons.io.FilenameUtils.getExtension
import static org.apache.commons.io.FilenameUtils.removeExtension

@Singleton
class DoopAnalysisFamily implements AnalysisFamily {

	@Override
	String getName() { "doop" }

	@Override
	void init() {}

	@Override
	List<AnalysisOption> supportedOptions() {
		return SUPPORTED_OPTIONS
	}

	private static List<AnalysisOption> SUPPORTED_OPTIONS = [
			/* Start LogicBlox related options */
			new AnalysisOption<String>(
					id: "LOGICBLOX_HOME",
					value: System.getenv("LOGICBLOX_HOME"),
					cli: false
			),
			new AnalysisOption<String>(
					id: "LD_LIBRARY_PATH", //the value is set based on LOGICBLOX_HOME
					value: null,
					cli: false
			),
			new AnalysisOption<String>(
					id: "BLOXBATCH", //the value is set based on LOGICBLOX_HOME
					value: null,
					cli: false
			),
			new AnalysisOption<String>(
					id: "BLOX_OPTS",
					value: null,
					cli: false
			),
			/* End LogicBlox related options */
			/* Start Main options */
			new BooleanAnalysisOption(
					id: "LB3",
					name: "lb",
					value: false,
					webUI: true,
					isAdvanced: true
			),
			new AnalysisOption<String>(
					id: "ANALYSIS",
					name: "analysis",
					argName: "NAME",
					description: "The name of the analysis.",
					value: null,
					validValues: analysesNames(Doop.analysesPath),
					isMandatory: true,
					webUI: true
			),
			new AnalysisOption<String>(
					id: "INPUTS",
					name: "inputFiles",
					argName: "FILES",
					value: null,
					isMandatory: false,
					webUI: true
			),
			new AnalysisOption<String>(
					id: "LIBRARIES",
					name: "libraryFiles",
					argName: "LIBRARIES",
					value: null,
					isMandatory: false,
					webUI: true
			),
			new AnalysisOption<String>(
					id: "USER_SUPPLIED_ID",
					name: "id",
					argName: "ID",
					value: null,
					webUI: true
			),

			new AnalysisOption<String>(
					id: "MAIN_CLASS",
					name: "main",
					argName: "CLASS",
					description: "Specify the main class.",
					value: null,
					webUI: true
			),
			new AnalysisOption<String>(
					id: "IMPORT_PARTITIONS",
					name: "import-partitions",
					argName: "file",
					description: "Specify the partitions.",
					value: null,
					webUI: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "TAMIFLEX",
					name: "tamiflex",
					argName: "FILE",
					forCacheID: true,
					isFile: true,
					description: "Use file with tamiflex data for reflection.",
					value: null,
					webUI: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "SANITY",
					name: "sanity",
					description: "Load additional logic for sanity checks.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "CACHE",
					name: "cache",
					description: "The analysis will use the cached facts, if they exist.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "SEPARATE_EXCEPTION_OBJECTS",
					name: "disable-inferTypeStructure-exceptions",
					value: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "NO_SSA",
					name: "no-ssa",
					description: "Disable the default policy of using ssa transformation on input.",
					value: false,
			),
			new BooleanAnalysisOption(
					id: "SSA",
					value: true,
					forCacheID: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "RUN_JPHANTOM",
					name: "run-jphantom",
					description: "Run jphantom for non-existent referenced jars.",
					value: false,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "RUN_FLOWDROID",
					name: "run-flowdroid",
					description: "Run soot-infoflow-android to generate dummy main method.",
					value: false,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "GENERATE_JIMPLE",
					name: "generate-jimple",
					description: "Generate Jimple/Shimple files along with .facts files",
					value: false,
					forCacheID: true
			),
			new AnalysisOption<Boolean>(
					id: "UNIQUE_FACTS",
					name: "unique-facts",
					description: "Eliminate redundancy from .facts files.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "DACAPO",
					name: "dacapo",
					description: "Load additional logic for DaCapo (2006) benchmarks properties.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "DACAPO_BACH",
					name: "dacapo-bach",
					description: "Load additional logic for DaCapo (Bach) benchmarks properties.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "ONLY_APPLICATION_CLASSES_FACT_GEN",
					name: "only-application-classes-fact-gen",
					value: false,
					forCacheID: true
			),
			new IntegerAnalysisOption(
					id: "FACTGEN_CORES",
					name: "fact-gen-cores",
					description: "Number of cores to use for parallel fact generation.",
					argName: "NUMBER",
					value: null
			),
			new AnalysisOption<String>(
					id: "APP_REGEX",
					name: "regex",
					argName: "EXPRESSION",
					description: "A regex expression for the Java package names of the analyzed application.",
					value: null,
					forCacheID: true,
					webUI: true
			),
			new AnalysisOption<String>(
					id: "PLATFORM",
					name: "platform",
					argName: "PLATFORM",
					description: "The platform and platform version to perform the analysis on (e.g. java_3, java_4 etc., android_22_stubs, android_22_fulljars). For Android, the plaftorm suffix can either be 'stubs' (provided by the Android SDK) or 'fulljars' (a custom Android build). default: java_7",
					value: "java_7",
					webUI: true,
					forCacheID: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "ANDROID",
					name: "android",
					description: "If true the analysis is ran on an Android app",
					value: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "PLATFORMS_LIB",
					name: "platforms-lib",
					description: "The path to the platform libs directory.",
					value: System.getenv("DOOP_PLATFORMS_LIB") ?: Doop.ARTIFACTORY_PLATFORMS_URL,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "CFG_ANALYSIS",
					name: "cfg",
					value: true,
					cli: false
			),
			/* End Main options */
			/* Start preprocessor normal flags */
			new BooleanAnalysisOption(
					id: "NO_MERGES",
					name: "no-merges",
					description: "No merges for string constants",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS",
					name: "distinguish-reflection-only-string-constants",
					description: "Merge all string constants except those useful for reflection",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_ALL_STRING_CONSTANTS",
					name: "distinguish-all-string-constants",
					description: "Treat string constants as regular objects",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_ALL_STRING_BUFFERS",
					name: "distinguish-all-string-buffers",
					description: "Avoids merging string buffer objects (not recommended).",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_STRING_BUFFERS_PER_PACKAGE",
					name: "distinguish-string-buffers-per-package",
					description: "Merges string buffer objects only on a per-package basis (default behavior for reflection-classic).",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "EXCLUDE_IMPLICITLY_REACHABLE_CODE",
					name: "exclude-implicitly-reachable-code",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "COARSE_GRAINED_ALLOCATION",
					name: "coarse-grained-allocation-sites",
					description: "Aggressively inferTypeStructure allocation sites for all regular object types, in lib and app alike.",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "NO_MERGE_LIBRARY_OBJECTS",
					name: "no-inferTypeStructure-library-objects",
					description: "Disable the default policy of merging library (non-collection) objects of the same type per-method.",
					value: false,
					webUI: true,
			),
			new BooleanAnalysisOption(
					id: "MERGE_LIBRARY_OBJECTS_PER_METHOD",
					value: true,
					forPreprocessor: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "CONTEXT_SENSITIVE_LIBRARY_ANALYSIS",
					name: "cs-library",
					description: "Enable context-sensitive analysis for internal library objects.",
					value: false,
					webUI: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION",
					name: "reflection",
					description: "Enable logic for handling Java reflection.",
					value: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_CLASSIC",
					name: "reflection-classic",
					description: "Enable (classic subset of) logic for handling Java reflection.",
					value: false,
					webUI: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_SUBSTRING_ANALYSIS",
					name: "reflection-substring-analysis",
					description: "Allows reasoning on what substrings may yield reflection objects.",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_CONTEXT_SENSITIVITY",
					name: "reflection-context-sensitivity",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_HIGH_SOUNDNESS_MODE",
					name: "reflection-high-soundness-mode",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_SPECULATIVE_USE_BASED_ANALYSIS",
					name: "reflection-speculative-use-based-analysis",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_INVENT_UNKNOWN_OBJECTS",
					name: "reflection-invent-unknown-objects",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "GROUP_REFLECTION_STRINGS",
					name: "reflection-coloring",
					value: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISABLE_METHOD_HANDLES",
					name: "disable-method-handles",
					description: "Disable handling of 'invokedynamic' and method handles.",
					value: false,
					webUI: false,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_REFINED_OBJECTS",
					name: "reflection-refined-objects",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_DYNAMIC_PROXIES",
					name: "reflection-dynamic-proxies",
					description: "Enable handling of the Java dynamic proxy API.",
					value: false,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "GENERATE_PROGUARD_KEEP_DIRECTIVES",
					name: "gen-proguard-keep",
					description: "Generate keep directives for proguard",
					value: false,
					webUI: true,
					isAdvanced: true
			),
			new BooleanAnalysisOption(
					id: "DISCOVER_TESTS",
					name: "discover-tests",
					description: "Discover testing code (e.g. marked with JUnit annotations).",
					value: false,
					webUI: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISCOVER_MAIN_METHODS",
					name: "discover-main-methods",
					description: "Discover main() methods.",
					value: false,
					webUI: true,
					forPreprocessor: true
			),
			/* End preprocessor normal flags */
			/* Start Souffle related options */
			new IntegerAnalysisOption(
					id: "SOUFFLE_JOBS",
					name: "souffle-jobs",
					argName: "NUMBER",
					value: 4
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_DEBUG",
					name: "souffle-debug",
					value: false
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_PROFILE",
					name: "souffle-profile",
					value: false
			),
			/* Start Souffle related options */

			//Information-flow, etc.
			new AnalysisOption<String>(
					id: "FEATHERWEIGHT_ANALYSIS",
					name: "featherweight-analysis",
					description: "Perform a featherweight analysis (global state and complex objects immutable).",
					value: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "INFORMATION_FLOW",
					name: "information-flow",
					argName: "APPLICATION_PLATFORM",
					description: "Load additional logic to perform information flow analysis.",
					value: null,
					validValues: informationFlowPlatforms(Doop.addonsPath, Doop.souffleAddonsPath),
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "INFORMATION_FLOW_HIGH_SOUNDNESS",
					name: "information-flow-high-soundness",
					description: "Enter high soundness mode for information flow microbenchmarks.",
					value: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "INFORMATION_FLOW_EXTRA_CONTROLS",
					name: "information-flow-extra-controls",
					argName: "CONTROLS",
					description: "Load additional sensitive layout control from string triplets \"id1,type1,parent_id1,...\".",
					value: null,
					forPreprocessor: true
			),
			new AnalysisOption(
					id: "OPEN_PROGRAMS",
					name: "open-programs",
					argName: "STRATEGY",
					description: "Create analysis entry points and environment using various strategies: servlets-only or concrete-types.",
					value: null,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "OPEN_PROGRAMS_IMMUTABLE_CTX",
					name: "open-programs-context-insensitive-entrypoints",
					value: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "OPEN_PROGRAMS_IMMUTABLE_HCTX",
					name: "open-programs-heap-context-insensitive-entrypoints",
					value: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "IGNORE_MAIN_METHOD",
					name: "ignore-main-method",
					description: "If main class is not given explicitly, do not try to discover it from jar/filename info. Open-program analysis variant will be triggered in this case.",
					webUI: true,
					value: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "HEAPDL",
					name: "heapdl",
					argName: "FILE",
					description: "Use dynamic information from memory dump, using HeapDL. Takes a file in HPROF format, generated by a JVM invocation of the form:\n java -agentlib:hprof=heap=dump,format=b,depth=8 ...",
					value: null,
					isFile: true,
					webUI: true,
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "HEAPDL_NOSTRINGS",
					name: "heapdl-nostrings",
					forCacheID: true,
					description: "Do not model string values uniquely in a memory dump.",
					value: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "IMPORT_DYNAMIC_FACTS",
					name: "import-dynamic-facts",
					argName: "FACTS_FILE",
					description: "Use dynamic information from file.",
					value: null,
					forPreprocessor: true
			),

			/* Start non-standard flags */
			new BooleanAnalysisOption(
					id: "X_STATS_FULL",
					name: "Xstats-full",
					description: "Load additional logic for collecting statistics.",
					value: false,
					nonStandard: true
			),
			new BooleanAnalysisOption(
					id: "X_STATS_NONE",
					name: "Xstats-none",
					description: "Do not load logic for collecting statistics.",
					value: false,
					nonStandard: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_STATS_AROUND",
					name: "Xstats-around",
					argName: "FILE",
					isFile: true,
					description: "Load custom logic for collecting statistics.",
					value: false,
					nonStandard: true
			),
			new AnalysisOption<String>(
					id: "X_STOP_AT_FACTS",
					name: "Xstop-at-facts",
					argName: "OUT_DIR",
					isFile: true,
					description: "Only generate facts and exit. Link result to OUT_DIR",
					value: false,
					nonStandard: true
			),
			new BooleanAnalysisOption(
					id: "X_STOP_AT_INIT",
					name: "Xstop-at-init",
					description: "Initialize database with facts and exit.",
					value: false,
					nonStandard: true
			),
			new BooleanAnalysisOption(
					id: "X_STOP_AT_BASIC",
					name: "Xstop-at-basic",
					description: "Run the basic analysis and exit.",
					value: false,
					nonStandard: true
			),
			new BooleanAnalysisOption(
					id: "X_DRY_RUN",
					name: "Xdry-run",
					description: "Do a dry run of the analysis.",
					value: false,
					nonStandard: true
			),
			new BooleanAnalysisOption(
					id: "X_SERVER_LOGIC",
					name: "Xserver-logic",
					description: "Run server queries under addons/server-logic",
					value: false,
					nonStandard: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_EXTRA_METRICS",
					name: "Xextra-metrics",
					description: "Run extra metrics logic under addons/statistics",
					value: false,
					nonStandard: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "X_START_AFTER_FACTS",
					name: "Xstart-after-facts",
					description: "Import facts from OUT_DIR and start the analysis",
					argName: "OUT_DIR",
					isDir: true,
					nonStandard: true,
					forPreprocessor: true
			),
			new IntegerAnalysisOption(
					id: "X_SERVER_LOGIC_THRESHOLD",
					name: "Xserver-logic-threshold",
					argName: "THRESHOLD",
					description: "Threshold when reporting point-to information in server logic (per points-to set). default: 1000",
					value: 1000,
					nonStandard: true,
					webUI: true,
					forPreprocessor: true,
					isAdvanced: true
			),
			new AnalysisOption<String>(
					id: "X_R_OUT_DIR",
					name: "XR-out-dir",
					description: "When linking AAR inputs, place generated R code in R_OUT_DIR",
					argName: "R_OUT_DIR",
					isDir: true,
					nonStandard: true
			),
			/* End non-standard flags */

			/* TODO: deprecated or broken? */
			new AnalysisOption<String>(
					id: "MUST",
					name: "must",
					description: "Run the must-alias analysis.",
					cli: false
			),
			new AnalysisOption<Boolean>(
					id: "MUST_AFTER_MAY",
					value: false,
					cli: false,
					forPreprocessor: true
			),
			new AnalysisOption<Boolean>(
					id: "TRANSFORM_INPUT",
					name: "transform-input",
					description: "Transform input by removing redundant instructions.",
					value: false,
					forPreprocessor: true,
					cli: false
			),
			new AnalysisOption<List<String>>(
					id: "DYNAMIC",
					name: "dynamic",
					argName: "FILE",
					isFile: true,
					description: "File with tab-separated data for Config:DynamicClass. Separate multiple files with a space.",
					value: [],
					cli: false
			),
			new AnalysisOption<Boolean>(
					id: "REFINE",
					value: false,
					cli: false
			),
			new AnalysisOption<Boolean>(
					id: "RUN_AVERROES",
					name: "run-averroes",
					description: "Run averroes to create a placeholder library.",
					value: false,
					forCacheID: true,
					cli: false
			),
	]

	private static List<String> analysesNames(String doopAnalysesDir) {
		List<String> analyses = []
		if (doopAnalysesDir)
			new File(doopAnalysesDir).eachDir { File dir ->
				def f = new File(dir, "analysis.logic")
				if (f.exists() && f.isFile()) analyses << dir.name
			}
		return analyses.sort()
	}

    private static List<String> informationFlowPlatforms(String lbDir,
                                                         String souffleDir) {
        List<String> platforms_LB = []
        List<String> platforms_Souffle = []

        Closure scan = { ifDir ->
            if (ifDir) {
                new File("${ifDir}/information-flow")?.eachFile { File f ->
                    String n = f.getName()
                    String base = removeExtension(n)
                    int platformEndIdx = base.lastIndexOf(INFORMATION_FLOW_SUFFIX)
                    if (platformEndIdx != -1) {
                        String ext = getExtension(n)
                        if (ext.equals("logic")) {
                            platforms_LB << base.substring(0, platformEndIdx)
                        } else if (ext.equals("dl")) {
                            platforms_Souffle << base.substring(0, platformEndIdx)
                        }
                    }
                }
            }
        }

        scan(lbDir)
        scan(souffleDir)

        List<String> platforms =
            (platforms_Souffle.collect {
                it + ((it in platforms_LB) ? "" : " (Souffle-only)")
            }) +
            (platforms_LB.findAll { !(it in platforms_Souffle) }
                         .collect { it + " (LB-only)"})
        return platforms.sort()
    }
}
