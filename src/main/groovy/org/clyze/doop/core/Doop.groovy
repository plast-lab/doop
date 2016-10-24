package org.clyze.doop.core

import org.apache.log4j.Logger
import org.clyze.doop.system.FileOps

/**
 * Doop initialization and supported options.
 */
class Doop {

    static final String SOOT_CHECKSUM_KEY = "soot"
    static final String ARTIFACTORY_PLATFORMS_URL = "http://centauri.di.uoa.gr:8081/artifactory/Platforms"

    static final List<AnalysisOption> ANALYSIS_OPTIONS = [
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
            cli:false,
            isAdvanced:true,
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
            value:false,
            webUI:true,
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"DISTINGUISH_STRING_BUFFERS_PER_METHOD",
            name:"distinguish-string-buffers-per-method",
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
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"MERGE_LIBRARY_OBJECTS_PER_METHOD",
            name:"disable-merge-library-objects",
            description:"Disable default policy of merging library (non-collection) objects of the same type per-method.",
            value:true,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"CONTEXT_SENSITIVE_LIBRARY_ANALYSIS",
            name:"enable-cs-library",
            description:"Enable context-sensitive analysis for internal library objects.",
            value:false,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"ENABLE_REFLECTION",
            name:"enable-reflection",
            description:"Enable logic for handling Java reflection.",
            value:false,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"ENABLE_REFLECTION_CLASSIC",
            name:"enable-reflection-classic",
            description:"Enable (classic subset of) logic for handling Java reflection.",
            value:false,
            webUI:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_SUBSTRING_ANALYSIS",
            name:"enable-reflection-substring-analysis",
            value:false,
            webUI:true,
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_CONTEXT_SENSITIVITY",
            name:"enable-reflection-context-sensitivity",
            value:false,
            webUI:true,
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_USE_BASED_ANALYSIS",
            name:"enable-reflection-use-based-analysis",
            value:false,
            webUI:true,
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_INVENT_UNKNOWN_OBJECTS",
            name:"enable-reflection-invent-unknown-objects",
            value:false,
            webUI:true,
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_REFINED_OBJECTS",
            name:"enable-reflection-refined-objects",
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
            webUI:true,
            forPreprocessor:true,
            isAdvanced:true,
        ),
        new AnalysisOption<Boolean>(
            id:"REFINE",
            value:false,
            cli:false
        ),
        new AnalysisOption<Boolean>(
            id:"SSA",
            name:"ssa",
            description:"Use ssa transformation for input.",
            value:false,
            forCacheID:true,
            webUI:true
        ),
        new AnalysisOption<Boolean>(
            id:"CACHE",
            name:"cache",
            description:"The analysis will use the cached facts, if they exist.",
            value:false,
            webUI:true
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
            forCacheID:true,
            webUI:true
        ),
        new AnalysisOption<Boolean>(
            id:"RUN_AVERROES",
            name:"run-averroes",
            description:"Run averroes to create a placeholder library.",
            value:false,
            forCacheID:true,
            webUI:true,
            isAdvanced:true,
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
            id:"INFORMATION_FLOW",
            name:"information-flow",
            description:"Load additional logic to perform information flow analysis.",
            value:false,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"ANDROID_SOURCES_AND_SINKS",
            name:"android-sources-and-sinks",
            description:"Load Android sources and sinks.",
            value:false,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"WEBAPPS_SOURCES_AND_SINKS",
            name:"webapps-sources-and-sinks",
            description:"Load webapps (Servlets) sources and sinks.",
            value:false,
            webUI:true,
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"OPEN_PROGRAMS_SERVLETS",
            name:"open-programs-servlets",
            description:"Create analysis entry points and environment to analyse servlet applications.",
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
        new AnalysisOption<Boolean>(
            id:"USE_ORIGINAL_NAMES",
            name:"use-original-names",
            value:false,
            forCacheID:true,
            webUI:true
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
			value:System.getenv("DOOP_PLATFORMS_LIB") ? System.getenv("DOOP_PLATFORMS_LIB") : ARTIFACTORY_PLATFORMS_URL,
			isAdvanced:true
        ),
        new AnalysisOption<String>(
			id:"PLATFORM",
			name:"platform",
			argName: "platform",
			description:"The platform and platform version to perform the analysis on (e.g. java_3, java_4 etc., android_22, android_24). default: java_7",
			value: "java_7",
			webUI: true,
			forCacheID: true
        ),
        new AnalysisOption<Boolean>(
             id:"FACT_GENERATION_CLASSIC",
             name:"fact-gen-classic",
             description:"Flag to enable the original sequential fact generation",
             value:false,
             webUI:true
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
        new AnalysisOption<String>(
            id:"X_STOP_AT_INIT",
            name:"XstopAt:init",
            description:"Initialize database with facts and exit.",
            value:false,
            nonStandard:true
        ),
        new AnalysisOption<String>(
            id:"X_STOP_AT_BASIC",
            name:"XstopAt:basic",
            description:"Run the basic analysis and exit.",
            value:false,
            nonStandard:true
        ),
        /* End of non-standard flags */
    ]

    static final List<String> OPTIONS_EXCLUDED_FROM_ID_GENERATION = [
        "LOGICBLOX_HOME",
        "LD_LIBRARY_PATH",
        "BLOXBATCH",
        "BLOX_OPTS",
        "CACHE",
        "PLATFORMS_LIB"
    ]

    // Not the best pattern, but limits the source code size :)
    static String doopHome
    static String doopOut
    static String doopCache
    static String logicPath
    static String factsPath
    static String addonsPath
    static String analysesPath

    /**
     * Initializes Doop.
     * @param homePath   The doop home directory (sets the doopHome variable, required).
     * @param outPath    The doop out directory (sets the doopOut variable, optional, defaults to 'out' under doopHome).
     * @param cachePath  The doop cache directory (sets the doopCache variable, optional, defaults to 'cache' under doopHome).
     * @return           The doop home directory.
     */
    static void initDoop(String homePath, String outPath, String cachePath) {

        doopHome = homePath
        if (!doopHome) throw new RuntimeException("DOOP_HOME environment variable is not set")
        FileOps.findDirOrThrow(doopHome, "DOOP_HOME environment variable is invalid: $doopHome")

        doopOut      = outPath ?: "$doopHome/out"
        doopCache    = cachePath ?: "$doopHome/cache"
        logicPath    = "$doopHome/logic"
        factsPath    = "$logicPath/facts"
        addonsPath   = "$logicPath/addons"
        analysesPath = "$logicPath/analyses"

        //create all necessary files/folders
        File f = new File(doopOut)
        f.mkdirs()
        FileOps.findDirOrThrow(f, "Could not create ouput directory: $doopOut")
        f = new File(doopCache)
        f.mkdirs()
        FileOps.findDirOrThrow(f, "Could not create cache directory: $doopCache")
    }

    /**
     * Creates the default analysis options.
     * @return Map<String, AnalysisOptions>.
     */
    static Map<String, AnalysisOption> createDefaultAnalysisOptions() {
        Map<String, AnalysisOption> options = [:]
        ANALYSIS_OPTIONS.each { AnalysisOption option ->
            options.put(option.id, AnalysisOption.newInstance(option))
        }
        return options
    }

    /**
     * Overrides the values of the map (the options values) with the values contained in the properties.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param options - the options to override.
     * @param properties - the properties to use.
     * @param filter - the filter to apply.
     * @return the original map of options with its values overridden by the ones contained in the properties.
     */
    static void overrideOptionsWithProperties(Map<String, AnalysisOption> options,
                                              Properties properties,
                                              Closure<Boolean> filter) {
        if (properties && properties.size() > 0) {
            properties.each { Map.Entry<String, String> entry->
                AnalysisOption option = options.get(entry.key.toUpperCase())
                if (option && entry.value && entry.value.trim().length() > 0) {
                    boolean filtered = filter ? filter.call(option) : true
                    if (filtered) {
                        setOptionFromProperty(option, entry.value)
                    }
                }
            }
        }
    }

    /**
     * Creates the analysis options by overriding the default options with the ones contained in the given properties.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param props - the properties.
     * @param filter - optional filter to apply before setting the option.
     * @return the default analysis options overridden by the values contained in the properties.
     */
    static Map<String, AnalysisOption> overrideDefaultOptionsWithProperties(Properties properties, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = createDefaultAnalysisOptions()
        overrideOptionsWithProperties(options, properties, filter)
        return options
    }

    /**
     * Creates the analysis options by processing the given properties.
     * An option is created only if filtered (the supplied filter returns true for the option).
     * @param props - the properties.
     * @param filter - optional filter to apply before setting the option.
     * @return the analysis options constructed by the values contained in the properties.
     */
    static Map<String, AnalysisOption> createOptionsFromProperties(Properties properties, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = [:]
        if (properties && properties.size() > 0) {
            ANALYSIS_OPTIONS.each { AnalysisOption option ->
                String property = properties.getProperty(option.id.toLowerCase())?.trim()
                if (property) {
                    boolean filtered = filter ? filter.call(option) : true
                    if (filtered) {
                        AnalysisOption o = AnalysisOption.newInstance(option)
                        setOptionFromProperty(o, property)
                        options.put(o.id, o)
                    }
                }
            }
        }
        return options
    }

    static void setOptionFromProperty(AnalysisOption option, String property) {
        if (option.id == "DYNAMIC") {
            option.value = property.split().collect { String s -> s.trim() }
        } else if (option.argName) {
            option.value = property
        } else {
            option.value = property.toBoolean()
        }
    }

    /**
     * Overrides the values of the map (the options values) with the values contained in the CLI options.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param options - the options to override.
     * @param properties - the properties to use.
     * @param filter - the filter to apply.
     * @return the original map of options with its values overridden by the ones contained in the CLI options.
     */
    static void overrideOptionsWithCLI(Map<String, AnalysisOption> options, OptionAccessor cli, Closure<Boolean> filter) {
        options.values().each { AnalysisOption option ->
            String optionName = option.name
            if (optionName) {
                def optionValue = cli[(optionName)]
                Logger.getRootLogger().debug "Processing $optionName"
                if (optionValue) { //Only true-ish values are of interest (false or null values are ignored)
                    boolean filtered = filter ? filter.call(option) : true
                    if (filtered) {
                        setOptionFromCLI(option, cli)
                    }
                }
            }
        }
    }

    /**
     * Creates the analysis options by overriding the default options with the ones contained in the given CLI options.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param cli - the CLI option accessor.
     * @param filter - optional filter to apply before setting the option.
     * @return the default analysis options overridden by the values contained in the CLI option accessor.
     */
    static Map<String, AnalysisOption> overrideDefaultOptionsWithCLI(OptionAccessor cli, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = createDefaultAnalysisOptions()
        overrideOptionsWithCLI(options, cli, filter)
        return options
    }

    static Map<String, AnalysisOption> createOptionsFromCLI(OptionAccessor cli, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = [:]
        ANALYSIS_OPTIONS.each { AnalysisOption option ->
            String optionName = option.name
            if (option.name) {
                def optionValue = cli.getProperty(optionName)
                if (optionValue) { //Only true-ish values are of interest (false or null values are ignored)
                    boolean filtered = filter ? filter.call(option) : true
                    if (filtered) {
                        AnalysisOption o = AnalysisOption.newInstance(option)
                        setOptionFromCLI(o, cli)
                        options.put(o.id, o)
                    }
                }
            }
        }
        return options
    }

    static void setOptionFromCLI(AnalysisOption option, OptionAccessor cli) {
        //Obscure cli builder feature: to get the value of a cl option as a List, you need to append an s to its short name
        if (option.id == "DYNAMIC") {
            //the short name of the DYNAMIC option is d, so we invoke ds
            option.value = cli.ds
        }
        else if (option.argName) {
            //if the cl option has an arg, the value of this arg defines the value of the respective
            // analysis option
            option.value = cli[(option.name)]
        } else {
            //the cl option has no arg and thus it is a boolean flag, toggling the default value of
            // the respective analysis option
            option.value = !option.value
        }
    }
}
