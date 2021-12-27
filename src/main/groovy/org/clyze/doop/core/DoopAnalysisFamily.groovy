package org.clyze.doop.core

import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils
import org.clyze.analysis.*
import org.clyze.doop.common.Parameters

import static DoopAnalysis.INFORMATION_FLOW_SUFFIX
import static org.apache.commons.io.FilenameUtils.getExtension
import static org.apache.commons.io.FilenameUtils.removeExtension

@CompileStatic
class DoopAnalysisFamily implements AnalysisFamily {

	public static final String DOOP_PLATFORMS_LIB_ENV = "DOOP_PLATFORMS_LIB"
	private static final String DEFAULT_JAVA_PLATFORM = "java_8"
	private static final String GROUP_ENGINE = "Datalog engine"
	private static final String GROUP_FACTS = "Fact generation"
	private static final String GROUP_HEAP_SNAPSHOTS = "Heap snapshots"
	private static final String GROUP_INFORMATION_FLOW = "Information flow"
	private static final String GROUP_NATIVE = "Native code"
	private static final String GROUP_OPEN_PROGRAMS = "Open programs"
	private static final String GROUP_PYTHON = "Python"
	private static final String GROUP_REFLECTION = "Reflection"
	private static final String GROUP_STATS = "Statistics"
	private static final String GROUP_ENTRY_POINTS = "Entry points"
	private static final String GROUP_SERVER = "Server logic"
	private static final String GROUP_DATA_FLOW = "Data flow"
	private static final String GROUP_EXPERIMENTAL = "Xtras"

	private static final int SERVER_DEFAULT_THRESHOLD = 1000
	private static final int DEFAULT_JOBS = 4
	private static final String DEFAULT_NATIVE_BACKEND = ''
	static final String INPUT_ID_OPT = 'input-id'
	static final String CACHE_OPT = 'cache'

	static final String NATIVE_BACKEND_BINUTILS = 'binutils'
	static final String NATIVE_BACKEND_BUILTIN = 'builtin'
	static final String NATIVE_BACKEND_RADARE = 'radare'

	static final String USE_ANALYSIS_BINARY_NAME = 'use-analysis-binary'

	static final String STATS_NONE = 'none'
	static final String STATS_DEFAULT = 'default'
	static final String STATS_FULL = 'full'

	static final String SOUFFLE_COMPILED = 'compiled'
	static final String SOUFFLE_INTERPRETED = 'interpreted'
	static final String SOUFFLE_TRANSLATED = 'translated'

	/**
	 * Special value of open programs that disables their automatic activation.
	 * Useful when entry points are missing from the command line and are
	 * only declared in logic.
	 */
	static final String FORCE_OPEN_PROGRAMS_DISABLED = 'disabled'

	@Override
	String getName() { "doop" }

	@Override
	void init() {}

	@Override
	List<AnalysisOption<?>> supportedOptions() { SUPPORTED_OPTIONS }

	@Override
	Map<String, AnalysisOption<?>> supportedOptionsAsMap() { supportedOptions().collectEntries { [(it.id): it] } }

	@Override
	void cleanDeploy() {
		File cacheDir = new File(Doop.doopCache)
		if (cacheDir.exists()) {
			println "Deleting: ${cacheDir.canonicalPath}"
			FileUtils.deleteQuietly(cacheDir)
		}
	}

	AnalysisOption getOptionByName(String n) {
		SUPPORTED_OPTIONS.find { it.name == n }
	}

	private static List<AnalysisOption<?>> SUPPORTED_OPTIONS = [
			/* Start Main options */
			new AnalysisOption<String>(
					id: "USER_SUPPLIED_ID",
					name: "id",
					description: "The analysis id. If omitted, it is automatically generated.",
					argName: "ID"
			),
			new AnalysisOption<String>(
					id: "ANALYSIS",
					name: "analysis",
					optName: "a",
					argName: "NAME",
					description: "The name of the analysis.",
					validValues: validAnalyses as Set<String>,
					isMandatory: true
			),
			new AnalysisOption<File>(
					id: "OUT_DIR",
					cli: false
			),
			new AnalysisOption<File>(
					id: "CACHE_DIR",
					cli: false
			),
			new IntegerAnalysisOption(
					id: "TIMEOUT",
					name: "timeout",
					optName: "t",
					argName: "MINUTES",
					description: "The analysis max allocated execution time. Measured in minutes.",
					value: 90, // Minutes
					cli: false
			),
			new AnalysisOption<String>(
					id: "MAX_MEMORY",
					name: "max-memory",
					argName: "MEMORY_SIZE",
					description: "The maximum memory that the analysis can consume (does not include memory needed by fact generation). Example values: 2m, 4g."
			),
			new AnalysisOption<List<String>>(
					id: "INPUTS",
					name: "input-file",
					optName: "i",
					description: "The (application) input files of the analysis. Accepted formats: .jar, .war, .apk, .aar, maven-id",
					value: [],
					multipleValues: true,
					argName: "INPUT",
					argInputType: InputType.INPUT,
					isMandatory: true
			),
			new AnalysisOption<List<String>>(
					id: "LIBRARIES",
					name: "library-file",
					optName: "l",
					description: "The dependency/library files of the application. Accepted formats: .jar, .apk, .aar",
					value: [],
					multipleValues: true,
					argName: "LIBRARY",
					argInputType: InputType.LIBRARY,
					isMandatory: false
			),
			new AnalysisOption<List<String>>(
					id: "PLATFORMS",
					name: "platform-files",
					multipleValues: true,
					argInputType: InputType.LIBRARY,
					cli: false
			),
			new AnalysisOption<List<String>>(
					id: "HEAPDLS",
					name: "heapdl-file",
					group: GROUP_HEAP_SNAPSHOTS,
					description: "Use dynamic information from memory dump, using HeapDL. Takes one or more files (`.hprof` format or stack traces).",
					value: [],
					multipleValues: true,
					argName: "HEAPDLS",
					argInputType: InputType.HEAPDL,
					forCacheID: true,
					forPreprocessor: true,
			),
			new AnalysisOption<String>(
					id: "PLATFORM",
					name: "platform",
					argName: "PLATFORM",
					description: "The platform on which to perform the analysis. For Android, the plaftorm suffix can either be 'stubs' (provided by the Android SDK), 'fulljars' (a custom Android build), or 'apks' (custom Dalvik equivalent). Default: ${DEFAULT_JAVA_PLATFORM}.",
					value: DEFAULT_JAVA_PLATFORM,
					validValues: DoopAnalysisFactory.availablePlatforms,
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "PLATFORMS_LIB",
					name: "platforms-lib",
					description: "The path to the platform libs directory.",
					value: System.getenv(DOOP_PLATFORMS_LIB_ENV),
					cli: false
			),
			new AnalysisOption<String>(
					id: "USE_LOCAL_JAVA_PLATFORM",
					name: "use-local-java-platform",
					description: "The path to the Java platform to use.",
					argName: "PATH"
			),
			new AnalysisOption<List<String>>(
					id: "DEFINE_CPP_MACRO",
					name: "define-cpp-macro",
					description: "Define a C preprocessor macro that will be available in analysis logic.",
					value: [],
					multipleValues: true,
					argName: "MACRO",
					forPreprocessor: true
			),
			new AnalysisOption<List<String>>(
					id: "MAIN_CLASS",
					name: "main",
					group: GROUP_ENTRY_POINTS,
					argName: "MAIN",
					description: "Specify the main class(es) separated by spaces.",
					value: [] as List<String>,
					multipleValues: true,
					forCacheID: true
			),
			new AnalysisOption<String>(
					id: "CONFIGURATION",
					name: "configuration",
					description: "Analysis Configuration",
					value: "ContextInsensitiveConfiguration",
					cli: false,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "X_IMPORT_PARTITIONS",
					name: "Ximport-partitions",
					group: GROUP_EXPERIMENTAL,
					argName: "FILE",
					description: "Specify the partitions.",
					argInputType: InputType.MISC,
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "TAMIFLEX",
					name: "tamiflex",
					group: GROUP_REFLECTION,
					description: "Use file with tamiflex data for reflection.",
					argName: "FILE",
					argInputType: InputType.MISC,
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "KEEP_SPEC",
					name: "keep-spec",
					group: GROUP_ENTRY_POINTS,
					argName: "FILE",
					argInputType: InputType.MISC,
					forCacheID: true,
					description: "Give a 'keep' specification."
			),
			new AnalysisOption<String>(
					id: "SPECIAL_CONTEXT_SENSITIVITY_METHODS",
					name: "special-cs-methods",
					argName: "FILE",
					argInputType: InputType.MISC,
					description: "Use a file that specifies special context sensitivity for some methods.",
					forPreprocessor: true,	
					forCacheID: true
			),
			new AnalysisOption<String>(
					id: "USER_DEFINED_PARTITIONS",
					name: "user-defined-partitions",
					argName: "FILE",
					argInputType: InputType.MISC,
					description: "Use a file that specifies the partitions of the analyzed program.",
					forPreprocessor: true,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "SANITY",
					name: "sanity",
					description: "Load additional logic for sanity checks.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "NO_STANDARD_EXPORTS",
					name: "no-standard-exports",
					description: "Do not export standard relations.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "CACHE",
					name: CACHE_OPT,
					group: GROUP_FACTS,
					description: "The analysis will use the cached facts, if they exist."
			),
			new BooleanAnalysisOption(
					id: "SEPARATE_EXCEPTION_OBJECTS",
					name: "disable-merge-exceptions",
					description: "Do not merge exception objects.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_NO_SSA",
					name: "Xno-ssa",
					group: GROUP_EXPERIMENTAL,
					description: "Disable the default policy of using SSA transformation on input.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "SSA",
					name: "ssa",
					group: GROUP_FACTS,
					value: true,
					forCacheID: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "RUN_JPHANTOM",
					name: "run-jphantom",
					description: "Run jphantom for non-existent referenced code.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "REPORT_PHANTOMS",
					name: "report-phantoms",
					group: GROUP_FACTS,
					description: "Report phantom methods/types during fact generation.",
					value: false
			),
			new BooleanAnalysisOption(
					id: "GENERATE_JIMPLE",
					name: "generate-jimple",
					group: GROUP_FACTS,
					description: "Generate Jimple/Shimple files along with .facts files.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "GENERATE_TAC",
					name: "generate-tac",
					group: GROUP_FACTS,
					description: "Generate Three Address Code experimental representation, along with .facts files.",
			),
			new BooleanAnalysisOption(
					id: "GENERATE_ARTIFACTS_MAP",
					name: "generate-artifacts-map",
					group: GROUP_FACTS,
					description: "Generate artifacts map.",
					value: false,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "SIMULATE_NATIVE_RETURNS",
					name: "simulate-native-returns",
					group: GROUP_NATIVE,
					description: "Assume native methods return mock objects.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "SCAN_NATIVE_CODE",
					name: "scan-native-code",
					group: GROUP_NATIVE,
					description: "Scan native code for specific patterns.",
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "NATIVE_CODE_BACKEND",
					name: "native-code-backend",
					group: GROUP_NATIVE,
					argName: "BACKEND",
					description: "Use back-end to scan native code (portable built-in, system binutils, Radare2).",
					validValues: [NATIVE_BACKEND_BUILTIN, NATIVE_BACKEND_BINUTILS, NATIVE_BACKEND_RADARE] as Set<String>,
					value: DEFAULT_NATIVE_BACKEND,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "ONLY_PRECISE_NATIVE_STRINGS",
					name: "only-precise-native-strings",
					group: GROUP_NATIVE,
					description: "Skip strings without enclosing function information.",
					forCacheID: true,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DACAPO",
					name: "dacapo",
					description: "Load additional logic for DaCapo (2006) benchmarks properties.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DACAPO_BACH",
					name: "dacapo-bach",
					description: "Load additional logic for DaCapo (Bach) benchmarks properties.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "WALA_FACT_GEN",
					name: "wala-fact-gen",
					group: GROUP_FACTS,
					description: "Use WALA to generate the facts.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "X_DEX_FACT_GEN",
					name: "Xdex",
					group: GROUP_EXPERIMENTAL,
					description: "Use custom front-end to generate facts for .apk inputs, using Soot for other inputs.",
					forCacheID: true
			),
			new AnalysisOption<String>(
					id: "DECODE_APK",
					name: "decode-apk",
					group: GROUP_FACTS,
					description: "Decode .apk inputs to facts directory.",
					value: false,
					cli: false,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "PYTHON",
					name: "python",
					forCacheID: true,
					cli: false
			),
			new IntegerAnalysisOption(
					id: "FACT_GEN_CORES",
					name: "fact-gen-cores",
					group: GROUP_FACTS,
					description: "Number of cores to use for parallel fact generation.",
					argName: "NUMBER"
			),
			new AnalysisOption<String>(
					id: "APP_REGEX",
					name: "regex",
					argName: "EXPRESSION",
					description: "A regex expression for the Java package names of the analyzed application.",
					forCacheID: true
			),
			new AnalysisOption<String>(
					id: "AUTO_APP_REGEX_MODE",
					name: "auto-app-regex-mode",
					argName: "MODE",
					description: "When no app regex is given, either compute an app regex for the first input ('first') or for all inputs ('all').",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "APP_ONLY",
					name: "app-only",
					description: "Only analyze the application input(s), ignore libraries/platform.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "ANDROID",
					name: "android",
					description: "Force Android mode for code inputs that are not in .apk format.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_LEGACY_ANDROID_PROCESSING",
					name: "Xlegacy-android-processing",
					group: GROUP_EXPERIMENTAL,
					description: "If true the analysis uses the legacy processor for Android resources."
			),
			new BooleanAnalysisOption(
					id: "X_LEGACY_SOOT_INVOCATION",
					name: "Xlegacy-soot-invocation",
					group: GROUP_EXPERIMENTAL,
					description: "If true, Soot will be invoked using a custom classloader (may use less memory, only supported on Java < 9)."
			),
			new BooleanAnalysisOption(
					id: "CFG_ANALYSIS",
					name: "cfg",
					description: "Perform a CFG analysis.",
					cli: true,
					forPreprocessor: true
			),
			/* End Main options */

			/* Start Scaler related options */
			new BooleanAnalysisOption(
					id: "SCALER_PRE_ANALYSIS",
					name: "Xscaler-pre",
					group: GROUP_EXPERIMENTAL,
					description: "Enable the analysis to be the pre-analysis of Scaler, and outputs the information required by Scaler.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "GENERICS_PRE_ANALYSIS",
					name: "Xgenerics-pre",
					group: GROUP_EXPERIMENTAL,
					description: "Enable precise generics pre-analysis to infer content types for Collections and Maps.",
					forPreprocessor: true
			),
			/* End Scaler related options */

			/* Start Zipper related options */
			new BooleanAnalysisOption(
					id: "X_ZIPPER_PRE_ANALYSIS",
					name: "Xzipper-pre",
					group: GROUP_EXPERIMENTAL,
					description: "Enable the analysis to be the pre-analysis of Zipper, and outputs the information required by Zipper.",
					forPreprocessor: true
			),
			new AnalysisOption(
					id: "X_ZIPPER",
					name: "Xzipper",
					group: GROUP_EXPERIMENTAL,
					description: "Use file with precision-critical methods selected by Zipper, these methods are analyzed context-sensitively.",
					argName: "FILE",
					argInputType: InputType.MISC,
					forCacheID: true,
					forPreprocessor: true
			),
			/* End Zipper related options */

			/* Start Python related options */
			new BooleanAnalysisOption(
					id: "SINGLE_FILE_ANALYSIS",
					name: "single-file-analysis",
					group: GROUP_PYTHON,
					description: "Flag to be passed to WALAs IR translator to produce IR that makes the analysis of a single script file easier.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "TENSOR_SHAPE_ANALYSIS",
					name: "tensor-shape-analysis",
					group: GROUP_PYTHON,
					description: "Enable tensor shape analysis for Python.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "FULL_TENSOR_PRECISION",
					name: "full-tensor-precision",
					group: GROUP_PYTHON,
					description: "Full precision tensor shape analysis (not guaranteed to finish).",
					forPreprocessor: true
			),
			/* End Python related options */

			/* Start preprocessor normal flags */
			new BooleanAnalysisOption(
					id: "NO_MERGES",
					name: "no-merges",
					description: "No merges for string constants.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "PRECISE_GENERICS",
					name: "Xprecise-generics",
					group: GROUP_EXPERIMENTAL,
					description: "Precise handling for maps and collections.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS",
					name: "distinguish-reflection-only-string-constants",
					group: GROUP_REFLECTION,
					description: "Merge all string constants except those useful for reflection.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_ALL_STRING_CONSTANTS",
					name: "distinguish-all-string-constants",
					description: "Treat string constants as regular objects.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_ALL_STRING_BUFFERS",
					name: "distinguish-all-string-buffers",
					description: "Avoids merging string buffer objects (not recommended).",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISTINGUISH_STRING_BUFFERS_PER_PACKAGE",
					name: "distinguish-string-buffers-per-package",
					group: GROUP_REFLECTION,
					description: "Merges string buffer objects only on a per-package basis (default behavior for reflection-classic).",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "EXCLUDE_IMPLICITLY_REACHABLE_CODE",
					name: "exclude-implicitly-reachable-code",
					group: GROUP_ENTRY_POINTS,
					description: "Don't make any method implicitly reachable.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "COARSE_GRAINED_ALLOCATION",
					name: "coarse-grained-allocation-sites",
					description: "Aggressively merge allocation sites for all regular object types, in lib and app alike.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "NO_MERGE_LIBRARY_OBJECTS",
					name: "no-merge-library-objects",
					description: "Disable the default policy of merging library (non-collection) objects of the same type per-method."
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
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_MODEL_STDLIB",
					name: "Xmodel-stdlib",
					group: GROUP_EXPERIMENTAL,
					description: "Model standard library APIs instead of analyzing their code.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION",
					name: "reflection",
					group: GROUP_REFLECTION,
					description: "Enable logic for handling Java reflection.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_CLASSIC",
					name: "reflection-classic",
					group: GROUP_REFLECTION,
					description: "Enable (classic subset of) logic for handling Java reflection."
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_SUBSTRING_ANALYSIS",
					name: "reflection-substring-analysis",
					group: GROUP_REFLECTION,
					description: "Allows reasoning on what substrings may yield reflection objects.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_CONTEXT_SENSITIVITY",
					name: "Xreflection-context-sensitivity",
					group: GROUP_EXPERIMENTAL,
					description: "Enable context-sensitive handling of reflection.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_HIGH_SOUNDNESS_MODE",
					name: "reflection-high-soundness-mode",
					group: GROUP_REFLECTION,
					description: "Enable extra rules for more sound handling of reflection.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_SPECULATIVE_USE_BASED_ANALYSIS",
					name: "reflection-speculative-use-based-analysis",
					group: GROUP_REFLECTION,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_INVENT_UNKNOWN_OBJECTS",
					name: "reflection-invent-unknown-objects",
					group: GROUP_REFLECTION,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "GROUP_REFLECTION_STRINGS",
					name: "Xreflection-coloring",
					group: GROUP_EXPERIMENTAL,
					description: "Merge strings that will not conflict in reflection resolution.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "EXTRACT_MORE_STRINGS",
					name: "extract-more-strings",
					group: GROUP_FACTS,
					description: "Extract more string constants from the input code (may degrade analysis performance).",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_METHOD_HANDLES",
					name: "reflection-method-handles",
					group: GROUP_REFLECTION,
					description: "Reflection-based handling of the method handle APIs.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_REFINED_OBJECTS",
					name: "reflection-refined-objects",
					group: GROUP_REFLECTION,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "REFLECTION_DYNAMIC_PROXIES",
					name: "reflection-dynamic-proxies",
					group: GROUP_REFLECTION,
					description: "Enable handling of the Java dynamic proxy API.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "LIGHT_REFLECTION_GLUE",
					name: "light-reflection-glue",
					group: GROUP_REFLECTION,
					description: "Handle some shallow reflection patterns without full reflection support.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "GENERATE_OPTIMIZATION_DIRECTIVES",
					name: "gen-opt-directives",
					description: "Generate additional relations for code optimization uses.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "SARIF",
					name: "sarif",
					description: "Output SARIF results for specific relations.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISCOVER_TESTS",
					name: "discover-tests",
					group: GROUP_ENTRY_POINTS,
					description: "Discover and treat test code (e.g. JUnit) as entry points.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DISCOVER_MAIN_METHODS",
					name: "discover-main-methods",
					group: GROUP_ENTRY_POINTS,
					description: "Discover main() methods.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DATA_FLOW_GOTO_LIB",
					name: "data-flow-goto-lib",
					group: GROUP_DATA_FLOW,
					description: "Allow data-flow logic to go into library code using CHA.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "DATA_FLOW_ONLY_LIB",
					name: "data-flow-only-lib",
					group: GROUP_DATA_FLOW,
					description: "Run data-flow logic only for library code.",
					forPreprocessor: true
			),
			/* End preprocessor normal flags */
			/* Start Souffle related options */
			new IntegerAnalysisOption(
					id: "SOUFFLE_JOBS",
					name: "souffle-jobs",
					group: GROUP_ENGINE,
					description: "Specify number of Souffle jobs to run (default: ${DEFAULT_JOBS}).",
					argName: "NUMBER",
					value: DEFAULT_JOBS
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_DEBUG",
					name: "souffle-debug",
					group: GROUP_ENGINE,
					description: "Enable profiling in the Souffle binary."
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_PROFILE",
					name: "souffle-profile",
					group: GROUP_ENGINE,
					description: "Enable profiling in the Souffle binary."
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_LIVE_PROFILE",
					name: "souffle-live-profile",
					group: GROUP_ENGINE,
					description: "Enable live profiling in the Souffle binary."
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_PROVENANCE",
					name: "souffle-provenance",
					group: GROUP_ENGINE,
					description: "Call the provenance browser."
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_FORCE_RECOMPILE",
					name: "souffle-force-recompile",
					group: GROUP_ENGINE,
					description: "Force recompilation of Souffle logic."
			),
			new AnalysisOption<String>(
					id: "USE_ANALYSIS_BINARY",
					name: USE_ANALYSIS_BINARY_NAME,
					group: GROUP_ENGINE,
					description: "Use precompiled analysis binary (for Windows compatibility).",
					argName: "PATH"
			),
			new AnalysisOption<String>(
					id: "SOUFFLE_MODE",
					name: "souffle-mode",
					group: GROUP_ENGINE,
					description: "How to run Souffle: compile to binary, use interpreter, only translate to C++.",
					validValues: [SOUFFLE_COMPILED, SOUFFLE_INTERPRETED, SOUFFLE_TRANSLATED] as Set<String>,
					value: SOUFFLE_COMPILED,
					argName: "MODE"
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_USE_FUNCTORS",
					name: "souffle-use-functors",
					group: GROUP_ENGINE,
					description: "Enable the use of user-defined functors in Souffle."
			),
			new BooleanAnalysisOption(
					id: "SOUFFLE_INCREMENTAL_OUTPUT",
					name: "souffle-incremental-output",
					group: GROUP_ENGINE,
					description: "Use the functor for incremental output in Souffle.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "VIA_DDLOG",
					name: "Xvia-ddlog",
					group: GROUP_EXPERIMENTAL,
					description: "Convert and run Souffle with DDlog.",
					forPreprocessor: true
			),
			/* End Souffle related options */

			//Information-flow, etc.
			new AnalysisOption<String>(
					id: "DISABLE_POINTS_TO",
					name: "disable-points-to",
					description: "Disable (most) points-to analysis reasoning. This should only be combined with analyses that compensate (e.g., types-only).",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "FEATHERWEIGHT_ANALYSIS",
					name: "featherweight-analysis",
					description: "Perform a featherweight analysis (global state and complex objects immutable).",
					forPreprocessor: true
			),
            new AnalysisOption<String>(
                    id: "CONSTANT_FOLDING",
                    name: "constant-folding",
                    description: "Enable constant folding logic.",
					forPreprocessor: true
            ),
			new AnalysisOption<String>(
					id: "SYMBOLIC_REASONING",
					name: "symbolic-reasoning",
					description: "Symbolic reasoning for expressions.",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "INFORMATION_FLOW",
					name: "information-flow",
					group: GROUP_INFORMATION_FLOW,
					argName: "APPLICATION_PLATFORM",
					description: "Load additional logic to perform information flow analysis.",
					validValues: informationFlowPlatforms(Doop.lbLogicPath, Doop.souffleLogicPath),
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "INFORMATION_FLOW_HIGH_SOUNDNESS",
					name: "information-flow-high-soundness",
					group: GROUP_INFORMATION_FLOW,
					description: "Enter high soundness mode for information flow microbenchmarks.",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "INFORMATION_FLOW_EXTRA_CONTROLS",
					name: "information-flow-extra-controls",
					group: GROUP_INFORMATION_FLOW,
					argName: "CONTROLS",
					description: "Load additional sensitive layout control from string triplets \"id1,type1,parent_id1,...\".",
					forCacheID: true,
					forPreprocessor: true
			),
			new AnalysisOption(
					id: "OPEN_PROGRAMS",
					name: "open-programs",
					group: GROUP_OPEN_PROGRAMS,
					argName: "STRATEGY",
					description: "Create analysis entry points and environment using various strategies (such as 'concrete-types' or 'jackee').",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "OPEN_PROGRAMS_IMMUTABLE_CTX",
					name: "open-programs-context-insensitive-entrypoints",
					group: GROUP_OPEN_PROGRAMS,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "OPEN_PROGRAMS_IMMUTABLE_HCTX",
					name: "open-programs-heap-context-insensitive-entrypoints",
					group: GROUP_OPEN_PROGRAMS,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "IGNORE_MAIN_METHOD",
					name: "ignore-main-method",
					group: GROUP_ENTRY_POINTS,
					description: "If main class is not given explicitly, do not try to discover it from jar/filename info. Open-program analysis variant may be triggered in this case.",
					forPreprocessor: true
			),

			new AnalysisOption<String>(
					id: "HEAPDL_NOSTRINGS",
					name: "heapdl-nostrings",
					group: GROUP_HEAP_SNAPSHOTS,
					forCacheID: true,
					description: "Do not model string values uniquely in a memory dump.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "HEAPDL_DYNAMICVARPOINTSTO",
					name: "heapdl-dvpt",
					group: GROUP_HEAP_SNAPSHOTS,
					forCacheID: true,
					description: "Import dynamic var-points-to information.",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "IMPORT_DYNAMIC_FACTS",
					name: "import-dynamic-facts",
					group: GROUP_HEAP_SNAPSHOTS,
					argName: "FACTS_FILE",
					argInputType: InputType.MISC,
					description: "Use dynamic information from file.",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "INPUT_ID",
					name: INPUT_ID_OPT,
					group: GROUP_FACTS,
					description: "Import facts from dir with id ID and start the analysis. Application/library inputs are ignored.",
					argName: "ID",
					argInputType: InputType.MISC,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "FACTS_ONLY",
					name: "facts-only",
					group: GROUP_FACTS,
					description: "Only generate facts and exit."
			),

			/* Start LogicBlox related options */
			new AnalysisOption<String>(
					id: "LOGICBLOX_HOME",
					group: GROUP_ENGINE,
					value: System.getenv("LOGICBLOX_HOME"),
					cli: false
			),
			new AnalysisOption<String>(
					id: "LD_LIBRARY_PATH", //the value is set based on LOGICBLOX_HOME
					group: GROUP_ENGINE,
					cli: false
			),
			new AnalysisOption<String>(
					id: "BLOXBATCH", //the value is set based on LOGICBLOX_HOME
					group: GROUP_ENGINE,
					cli: false
			),
			new AnalysisOption<String>(
					id: "BLOX_OPTS",
					group: GROUP_ENGINE,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "X_LB3",
					name: "Xlb",
					group: GROUP_EXPERIMENTAL,
					description: "Use legacy LB engine."
			),
			/* End LogicBlox related options */

			/* Start non-standard flags */
			new AnalysisOption<String>(
					id: "STATS_LEVEL",
					name: "stats",
					group: GROUP_STATS,
					argName: "LEVEL",
					description: "Set statistics collection logic.",
					validValues: [STATS_NONE, STATS_DEFAULT, STATS_FULL] as Set<String>
			),
			new BooleanAnalysisOption(
					id: "X_STATS_FULL",
					name: "Xstats-full",
					group: GROUP_STATS,
					description: "Load additional logic for collecting statistics.",
					forPreprocessor: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "X_STATS_NONE",
					name: "Xstats-none",
					group: GROUP_STATS,
					description: "Do not load logic for collecting statistics.",
					forPreprocessor: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "X_STATS_DEFAULT",
					name: "Xstats-default",
					group: GROUP_STATS,
					description: "Load default logic for collecting statistics.",
					forPreprocessor: true,
					cli: false
			),
			new BooleanAnalysisOption(
					id: "DONT_CACHE_FACTS",
					name: "dont-cache-facts",
					group: GROUP_FACTS,
					description: "Don't cache generated facts."
			),
			new BooleanAnalysisOption(
					id: "DRY_RUN",
					name: "dry-run",
					description: "Do a dry run of the analysis (generate facts and compile but don't run analysis logic)."
			),
			new BooleanAnalysisOption(
					id: "X_SERVER_LOGIC",
					name: "server-logic",
					group: GROUP_SERVER,
					description: "Run server queries under addons/server-logic.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_SERVER_CHA",
					name: "server-cha",
					group: GROUP_SERVER,
					description: "Run server queries related to CHA."
			),
			new BooleanAnalysisOption(
					id: "X_EXTRA_METRICS",
					name: "extra-metrics",
					group: GROUP_STATS,
					description: "Run extra metrics logic under addons/statistics.",
					forPreprocessor: false
			),
			new BooleanAnalysisOption(
					id: "X_ORACULAR_HEURISTICS",
					name: "Xoracular-heuristics",
					group: GROUP_EXPERIMENTAL,
					description: "Run sensitivity heuristics logic under addons/oracular.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_CONTEXT_DEPENDENCY_HEURISTIC",
					name: "Xcontext-dependency-heuristic",
					group: GROUP_EXPERIMENTAL,
					description: "Run context dependency heuristics logic under addons/oracular.",
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "EXTRA_LOGIC",
					name: "extra-logic",
					description: "Include files with extra rules.",
					argName: "FILE",
					argInputType: InputType.MISC,
					value: [],
					multipleValues: true
			),
			new AnalysisOption<List<String>>(
					id: "X_EXTRA_FACTS",
					name: "Xextra-facts",
					group: GROUP_EXPERIMENTAL,
					description: "Include files with extra facts.",
					argName: "FILE",
					argInputType: InputType.MISC,
					value: [],
					multipleValues: true,
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "X_CONTEXT_REMOVER",
					name: "Xcontext-remover",
					group: GROUP_EXPERIMENTAL,
					description: "Run the context remover for reduced memory use (only available in context-insensitive analysis).",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_SYMLINK_INPUT_FACTS",
					name: "Xsymlink-input-facts",
					group: GROUP_FACTS,
					description: "Use symbolic links instead of copying cached facts. Used with --${CACHE_OPT} or --${INPUT_ID_OPT}.",
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "X_LOW_MEM",
					name: "Xlow-mem",
					group: GROUP_EXPERIMENTAL,
					description: "Use less memory. Does not support all options."
			),
			new BooleanAnalysisOption(
					id: "X_ISOLATE_FACTGEN",
					name: "Xisolate-fact-generation",
					group: GROUP_EXPERIMENTAL,
					description: "Isolate invocations to the fact generator."
			),
			new BooleanAnalysisOption(
					id: "X_SERIALIZE_FACTGEN_COMPILATION",
					name: "Xserialize-factgen-compilation",
					group: GROUP_EXPERIMENTAL,
					description: "Do not run fact generation and compilation in parallel.",
					cli: false
			),
			new IntegerAnalysisOption(
					id: "X_SERVER_LOGIC_THRESHOLD",
					name: "server-logic-threshold",
					group: GROUP_SERVER,
					argName: "THRESHOLD",
					description: "Threshold when reporting points-to information in server logic (per points-to set). default: ${SERVER_DEFAULT_THRESHOLD}",
					value: SERVER_DEFAULT_THRESHOLD,
					forPreprocessor: true
			),
			new AnalysisOption<String>(
					id: "X_R_OUT_DIR",
					name: "XR-out-dir",
					group: GROUP_EXPERIMENTAL,
					description: "When linking .aar inputs, place generated R code in <R_OUT_DIR>.",
					argName: "R_OUT_DIR",
					argInputType: InputType.MISC
			),
			new BooleanAnalysisOption(
					id: "X_IGNORE_WRONG_STATICNESS",
					name: "Xignore-wrong-staticness",
					group: GROUP_EXPERIMENTAL,
					description: "Ignore 'wrong static-ness' errors in Soot.",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "X_IGNORE_FACTGEN_ERRORS",
					name: "Xignore-factgen-errors",
					group: GROUP_FACTS,
					description: "Continue with analysis despite fact generation errors.",
					forCacheID: true
			),
			new AnalysisOption<List<String>>(
					id: "ALSO_RESOLVE",
					name: "also-resolve",
					group: GROUP_FACTS,
					description: "Force resolution of class(es) by Soot.",
					value: [],
					multipleValues: true,
					argName: "CLASS",
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "THOROUGH_FACT_GEN",
					name: "thorough-fact-gen",
					group: GROUP_FACTS,
					description: "Attempt to resolve as many classes during fact generation (may take more time).",
					forCacheID: true
			),
			new IntegerAnalysisOption(
					id: "X_MONITORING_INTERVAL",
					name: "Xmonitoring-interval",
					group: GROUP_EXPERIMENTAL,
					argName: "INTERVAL",
					description: "Monitoring interval for sampling memory and cpu usage. default: 5sec",
					value: 5,
					cli: false
			),
			new AnalysisOption<String>(
					id: "X_FACTS_SUBSET",
					name: "Xfacts-subset",
					group: GROUP_FACTS,
					description: "Produce facts only for a subset of the given classes.",
					argName: "SUBSET",
					validValues: Parameters.FactsSubSet.valueSet(),
					forCacheID: true
			),
			new BooleanAnalysisOption(
					id: "UNIQUE_FACTS",
					name: "unique-facts",
					group: GROUP_FACTS,
					description: "Eliminate redundancy from .facts files.",
					forCacheID: true
			),
			/* End non-standard flags */

			/* TODO: deprecated or broken? */
			new AnalysisOption<String>(
					id: "MUST",
					name: "must",
					description: "Run the must-alias analysis.",
					cli: false
			),
			new BooleanAnalysisOption(
					id: "MUST_AFTER_MAY",
					cli: false,
					forPreprocessor: true
			),
			new BooleanAnalysisOption(
					id: "TRANSFORM_INPUT",
					name: "transform-input",
					description: "Transform input by removing redundant instructions.",
					forPreprocessor: true,
					cli: false
			),
	] as List<AnalysisOption<?>>

	private static List<String> analysesFor(File path, String fileToLookFor) {
		if (path == null || !path.exists()) {
			println "ERROR: Doop was not initialized correctly, could not read analyses names. Is environment variable DOOP_HOME set?"
			return []
		}
		List<String> analyses = []
		path.eachDir { File dir ->
			def f = new File(dir, fileToLookFor)
			if (f.exists() && f.file) analyses << dir.name
		}
		return analyses.sort()
	}

	private static List<String> getValidAnalyses() {
		return analysesSouffle() + ["----- (LB analyses) -----"] + analysesLB()
	}

	private static List<String> analysesSouffle() {
		try {
			if (!Doop.souffleAnalysesPath) {
				Doop.initDoopFromEnv()
			}
		} catch (e) {
			println "ERROR: Souffle logic path not found, set DOOP_HOME."
		}
		return Doop.souffleAnalysesPath ? analysesFor(new File(Doop.souffleAnalysesPath), "analysis.dl") : []
	}

	private static List<String> analysesLB() {
		try {
			if (!Doop.lbAnalysesPath) {
				Doop.initDoopFromEnv()
			}
		} catch (e) {
			println "ERROR: Could not initialize Doop: ${e.message}"
			e.printStackTrace()
		}
		if (!Doop.lbAnalysesPath) {
			println "WARNING: LB legacy logic path not found, set DOOP_HOME."
			return []
		}
		if (Doop.lbAnalysesPath) {
			File legacyPath = new File(Doop.lbAnalysesPath)
			if (legacyPath.exists()) {
				return analysesFor(legacyPath, "analysis.logic")
			}
		}
		return []
	}

	static SortedSet<String> informationFlowPlatforms(String lbDir, String souffleDir) {
		SortedSet<String> platforms_LB = new TreeSet<>()
		SortedSet<String> platforms_Souffle = new TreeSet<>()
		Closure scan = { ifDir ->
			if (ifDir) {
				File ifSubDir = new File("${ifDir}/addons/information-flow")
				if (!ifSubDir || !ifSubDir.exists()) {
					println "WARNING: Cannot process information flow directory: ${ifSubDir}"
					return
				}
				ifSubDir.eachFile { File f ->
					String n = f.name
					String base = removeExtension(n)
					int platformEndIdx = base.lastIndexOf(INFORMATION_FLOW_SUFFIX)
					if (platformEndIdx != -1) {
						String ext = getExtension(n)
						if (ext == "logic") {
							platforms_LB << base.substring(0, platformEndIdx)
						} else if (ext == "dl") {
							platforms_Souffle << base.substring(0, platformEndIdx)
						}
					}
				}
			}
		}

		scan(lbDir)
		scan(souffleDir)

		SortedSet<String> platforms = platforms_Souffle + platforms_LB
		return new TreeSet<>(platforms)
	}

    static Collection<FileInput> getAllInputs(Map<String, AnalysisOption<?>> options) {
        Collection<FileInput> inputs = new ArrayList<FileInput>()
        Closure<List<FileInput>> collector = { AnalysisOption<?> l -> (l.value as List<String>).collect {new FileInput(l.id, new File(it)) }}
        inputs += collector(options.get('INPUTS'))
        inputs += collector(options.get('LIBRARIES'))
        inputs += collector(options.get('HEAPDLS'))
        inputs += collector(options.get('PLATFORMS'))
        return inputs
    }

	@CompileStatic
	static class FileInput {
		final String id
		final File file

		FileInput(String id, File file) {
			this.id = id
			this.file = file
		}

		@Override
		String toString() {
			return id + '$' + file.canonicalPath
		}
	}
}
