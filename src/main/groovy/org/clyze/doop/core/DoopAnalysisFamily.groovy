package org.clyze.doop.core

import org.clyze.analysis.AnalysisFamily
import org.clyze.analysis.AnalysisOption

class DoopAnalysisFamily implements AnalysisFamily {

	@Override
	String getName() { return "doop" }

	@Override
	void init() {}

	@Override
	List<AnalysisOption> supportedOptions() {
		return [
			//LogicBlox related options (supporting different LogicBlox instance per analysis)
			new AnalysisOption<String>(
				id:"LOGICBLOX_HOME",
				value:System.getenv("LOGICBLOX_HOME"),
				cli:false
			),
			new AnalysisOption<String>(
				id:"LD_LIBRARY_PATH", //the value is set based on LOGICBLOX_HOME
				value:null,
				cli:false
			),
			new AnalysisOption<String>(
				id:"BLOXBATCH", //the value is set based on LOGICBLOX_HOME
				value:null,
				cli:false
			),
			new AnalysisOption<String>(
				id:"BLOX_OPTS",
				value:null,
				cli:false
			),
			//Main options
			new AnalysisOption<String>(
				id:"MAIN_CLASS",
				name:"main",
				argName:"mainClass",
				description:"Specify the main class.",
				value:null,
				webUI:true
			),
			new AnalysisOption<List<String>>(
				id:"DYNAMIC",
				name:"dynamic",
				argName:"FILE",
				isFile:true,
				description:"File with tab-separated data for Config:DynamicClass. Separate multiple files with a space.",
				value:[],
				cli:false
			),
			new AnalysisOption<String>(
				id:"TAMIFLEX",
				name:"tamiflex",
				argName:"FILE",
				forCacheID:true,
				isFile:true,
				description:"Use file with tamiflex data for reflection.",
				value:null,
				webUI:true
			),
			new AnalysisOption<String>(
				id:"CFG_ANALYSIS",
				name:"cfg",
				description:"Run a control flow graph analysis.",
				webUI:true
			),
			/* TODO: deprecated? */
			new AnalysisOption<String>(
				id:"MUST",
				name:"must",
				description:"Run the must-alias analysis.",
				webUI:true
			),
			new AnalysisOption<Boolean>(
				id:"MUST_AFTER_MAY",
				value:false,
				cli:false,
				forPreprocessor:true
			),
			/* Start of preprocessor constant flags */
			new AnalysisOption<Boolean>(
				id:"DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS",
				name:"distinguish-reflection-only-string-constants",
				description:"Merge all string constants except those useful for reflection",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"DISTINGUISH_ALL_STRING_CONSTANTS",
				name:"distinguish-all-string-constants",
				description:"Treat string constants as regular objects",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			/* End of preprocessor constant flags */
			/* Start of preprocessor normal flags */
			new AnalysisOption<Boolean>(
				id:"DISTINGUISH_ALL_STRING_BUFFERS",
				name:"distinguish-all-string-buffers",
				description:"Avoids merging string buffer objects (not recommended).",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"DISTINGUISH_STRING_BUFFERS_PER_PACKAGE",
				name:"distinguish-string-buffers-per-package",
				description:"Merges string buffer objects only on a per-package basis (default behavior for reflection-classic).",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"EXCLUDE_IMPLICITLY_REACHABLE_CODE",
				name:"exclude-implicitly-reachable-code",
				value:false,
				webUI:true,
				forPreprocessor:true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"COARSE_GRAINED_ALLOCATION",
				name:"coarse-grained-allocation-sites",
				description:"Aggressively merge allocation sites for all regular object types, in lib and app alike.",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"MERGE_LIBRARY_OBJECTS_PER_METHOD",
				name:"no-merge-library-objects",
				description:"Disable the default policy of merging library (non-collection) objects of the same type per-method.",
				value:true,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"CONTEXT_SENSITIVE_LIBRARY_ANALYSIS",
				name:"cs-library",
				description:"Enable context-sensitive analysis for internal library objects.",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION",
				name:"reflection",
				description:"Enable logic for handling Java reflection.",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_CLASSIC",
				name:"reflection-classic",
				description:"Enable (classic subset of) logic for handling Java reflection.",
				value:false,
				webUI:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_SUBSTRING_ANALYSIS",
				name:"reflection-substring-analysis",
				description:"Allows reasoning on what substrings may yield reflection objects.",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_CONTEXT_SENSITIVITY",
				name:"reflection-context-sensitivity",
				value:false,
				webUI:true,
				forPreprocessor:true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_HIGH_SOUNDNESS_MODE",
				name:"reflection-high-soundness-mode",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_SPECULATIVE_USE_BASED_ANALYSIS",
				name:"reflection-speculative-use-based-analysis",
				value:false,
				webUI:true,
				forPreprocessor: true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_INVENT_UNKNOWN_OBJECTS",
				name:"reflection-invent-unknown-objects",
				value:false,
				webUI:true,
				forPreprocessor:true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_REFINED_OBJECTS",
				name:"reflection-refined-objects",
				value:false,
				webUI:true,
				forPreprocessor:true,
				isAdvanced:true
			),
			new AnalysisOption<Boolean>(
				id:"REFLECTION_DYNAMIC_PROXIES",
				name:"reflection-dynamic-proxies",
				description:"Enable handling of the Java dynamic proxy API.",
				value:false,
				webUI:true,
				forPreprocessor:true,
				isAdvanced:true
			),

			new AnalysisOption<Boolean>(
				id:"TRANSFORM_INPUT",
				name:"transform-input",
				description:"Transform input by removing redundant instructions.",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			/* End of preprocessor normal flags */

			new AnalysisOption<Boolean>(
				id:"SEPARATE_EXCEPTION_OBJECTS",
				name:"disable-merge-exceptions",
				value:false,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"REFINE",
				value:false,
				cli:false
			),
			new AnalysisOption<Boolean>(
				id:"SSA",
				name:"no-ssa",
				description:"Disable the default policy of using ssa transformation on input.",
				value:true,
				forCacheID:true
			),
			new AnalysisOption<Boolean>(
				id:"CACHE",
				name:"cache",
				description:"The analysis will use the cached facts, if they exist.",
				value:false
			),
			new AnalysisOption<Boolean>(
				id:"SANITY",
				name:"sanity",
				description:"Load additional logic for sanity checks.",
				value:false,
				webUI:true
			),
			new AnalysisOption<Boolean>(
				id:"RUN_JPHANTOM",
				name:"run-jphantom",
				description:"Run jphantom for non-existent referenced jars.",
				value:false,
				forCacheID:true
			),
			new AnalysisOption<Boolean>(
				id:"RUN_FLOWDROID",
				name:"run-flowdroid",
				description:"Run soot-infoflow-android to generate dummy main method.",
				value:false,
				forCacheID:true
			),
            new AnalysisOption<Boolean>(
                id:"GENERATE_JIMPLE",
                name:"generate-jimple",
                description:"Generate Jimple/Shimple files along with .facts files",
                value:false,
                forCacheID:false
            ),
			new AnalysisOption<Boolean>(
				id:"RUN_AVERROES",
				name:"run-averroes",
				description:"Run averroes to create a placeholder library.",
				value:false,
				forCacheID:true
			),
			new AnalysisOption<Boolean>(
				id:"DACAPO",
				name:"dacapo",
				description:"Load additional logic for DaCapo (2006) benchmarks properties.",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"DACAPO_BACH",
				name:"dacapo-bach",
				description:"Load additional logic for DaCapo (Bach) benchmarks properties.",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<String>(
				id:"DACAPO_BENCHMARK",
				value:null,
				cli:false,
				forPreprocessor:true
			),
			new AnalysisOption<String>(
				id:"INFORMATION_FLOW",
				name:"information-flow",
				argName:"application-platform",
				description:"Load additional logic to perform information flow analysis.",
				value:null,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"INFORMATION_FLOW_PRECISE_SOURCES_AND_SINKS",
				name:"information-flow-precise-sources-and-sinks",
				description:"Use precise call graph for identifying sources and sinks.",
				value:false,
				forPreprocessor:true
			),
			new AnalysisOption<String>(
				id:"OPEN_PROGRAMS",
				name:"open-programs",
				description:"Create analysis entry points and environment using various strategies: servlets-only, concrete-types, or oracle.",
				value:null,
				argName:"strategy",
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"OPEN_PROGRAMS_IMMUTABLE_CTX",
				name:"open-programs-context-insensitive-entrypoints",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"OPEN_PROGRAMS_IMMUTABLE_HCTX",
				name:"open-programs-heap-context-insensitive-entrypoints",
				value:false,
				webUI:true,
				forPreprocessor:true
			),
			new AnalysisOption<Boolean>(
				id:"ONLY_APPLICATION_CLASSES_FACT_GEN",
				name:"only-application-classes-fact-gen",
				value:false,
				forCacheID:true,
				webUI:true
			),
			new AnalysisOption<String>(
				id:"APP_REGEX",
				name:"regex",
				argName:"regex-expression",
				description:"A regex expression for the Java package names of the analyzed application.",
				value:null,
				forCacheID:true,
				webUI:true
			),
			new AnalysisOption<String>(
				id:"PLATFORMS_LIB",
				name:"platforms-lib",
				description:"The path to the platform libs directory.",
				value: System.getenv("DOOP_PLATFORMS_LIB") ?: Doop.ARTIFACTORY_PLATFORMS_URL,
				cli: false
			),
			new AnalysisOption<String>(
				id:"PLATFORM",
				name:"platform",
				argName: "platform",
				description:"The platform and platform version to perform the analysis on (e.g. java_3, java_4 etc., android_22, android_24). default: java_7",
				value: "java_7",
				webUI: true,
				forCacheID:true
			),
			new AnalysisOption<Boolean>(
				id:"RUN_SERVER_LOGIC",
				name:"run-server-logic",
				description:"Run server queries under addons/server-logic"
			),
			/* Start of non-standard flags */
			new AnalysisOption<Boolean>(
				id:"X_STATS_FULL",
				name:"Xstats:full",
				description:"Load additional logic for collecting statistics.",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<Boolean>(
				id:"X_STATS_NONE",
				name:"Xstats:none",
				description:"Do not load logic for collecting statistics.",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<Boolean>(
				id:"X_STATS_AROUND",
				name:"Xstats:around",
				argName:"FILE",
				isFile:true,
				description:"Load custom logic for collecting statistics.",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<String>(
				id:"X_STOP_AT_FACTS",
				name:"XstopAt:facts",
				argName:"OUT_DIR",
				isFile:true,
				description:"Only generate facts and exit. Link result to OUT_DIR",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<Boolean>(
				id:"X_STOP_AT_INIT",
				name:"XstopAt:init",
				description:"Initialize database with facts and exit.",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<Boolean>(
				id:"X_STOP_AT_BASIC",
				name:"XstopAt:basic",
				description:"Run the basic analysis and exit.",
				value:false,
				nonStandard:true
			),
			new AnalysisOption<Boolean>(
				id:"X_DRY_RUN",
				name:"XdryRun",
				description:"Do a dry run of the analysis.",
				value:false,
				nonStandard:true
			),
			/* End of non-standard flags */
		]
	}
}
