package doop.core

import org.apache.log4j.Logger

/**
 * Doop initialization and supported options.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 */
class Doop {

    static final List<AnalysisOption> ANALYSIS_OPTIONS = [
        //LogicBlox related options (supporting different LogicBlox instance per analysis)
        new AnalysisOption<String>(
            id:"LOGICBLOX_HOME",
            description: "set the path to LogicBlox home (default: the value of the LOGICBLOX_HOME environment variable).",
            value:System.getenv("LOGICBLOX_HOME"),
            webUI:false,
            cli:true,
            name:"lbhome",
            argName: "path",
            isAdvanced:true
        ),
        new AnalysisOption<String>(
            id:"LD_LIBRARY_PATH", //the value is set based on LOGICBLOX_HOME
            value:null
        ),
        new AnalysisOption<String>(
            id:"BLOXBATCH", //the value is set based on LOGICBLOX_HOME
            value:null
        ),
        new AnalysisOption<String>(
            id:"BLOX_OPTS",
            value:null
        ),
        //Main options
        new AnalysisOption<String>(
            id:"MAIN_CLASS",
            description:'Specify the main class.',
            value:null,
            webUI: true,
            cli:true,
            name: "main",
            argName: 'mainClass'
        ),
        new AnalysisOption<List<String>>(
            id:"DYNAMIC",
            description:"File with tab-separated data for Config:DynamicClass. Separate multiple files with a comma.",
            value:[],
            webUI:true,
            cli:true,
            name:"dynamic",
            argName:"FILE",
            isFile:true
        ),
        new AnalysisOption<String>(
            id:"TAMIFLEX",
            description:"File with tamiflex data.",
            value:null,
            webUI:true,
            cli:true,
            name:"tamiflex",
            argName:"FILE",
            forPreprocessor:true,
            isFile:true
        ),
        new AnalysisOption<String>(
            id:"CLIENT_CODE",
            description:"Additional directory/file of client analysis to include.",
            value:null,
            webUI:true,
            cli:true,
            name:"client",
            argName:"FILE",
            isFile:true
        ),
        new AnalysisOption<String>(
            id:"CLIENT_EXTENSIONS",
            value:false,
            forPreprocessor:true
        ),
        /* Flags for must analyses */
        new AnalysisOption<String>(
            id:"MAY_PRE_ANALYSIS",
            description:"Use a may analysis before running the must analysis.",
            value:null,
            cli:true,
            webUI:true,
            name:"may-pre-analysis",
            argName:"may-analysis name"
        ),
        new AnalysisOption<Boolean>(
            id:"MUST_AFTER_MAY",
            value:false,
            forPreprocessor:true
        ),
        
        /* Start of preprocessor constant flags */
        new AnalysisOption<Boolean>(
            id:"DISTINGUISH_REFLECTION_STRING_CONSTANTS",
            value:true, // enabled by default in run script
            cli:true,
            webUI:true,
            name:"distinguish-reflection-string-constants",
            forPreprocessor: true,
            isAdvanced:true,
            flagType:PreprocessorFlag.CONSTANT_FLAG
        ),
        new AnalysisOption<Boolean>(
            id:"DISTINGUISH_ALL_STRING_CONSTANTS",
            value:false,
            cli:true,
            webUI:true,
            name:"distinguish-all-string-constants",
            forPreprocessor:true,
            isAdvanced:true,
            flagType:PreprocessorFlag.CONSTANT_FLAG
        ),        
        new AnalysisOption<Boolean>(
            id:"DISTINGUISH_NO_STRING_CONSTANTS",
            value:false,
            cli:true,
            webUI:true,
            name:"distinguish-no-string-constants",
            forPreprocessor: true,
            isAdvanced:true,
            flagType:PreprocessorFlag.CONSTANT_FLAG
        ),
        /* End of preprocessor constant flags] */

        /* Start of preprocessor normal flags] */
        new AnalysisOption<String>(
            id:"NO_MODELING_OF_NUMS_OR_NULL",
            value:true, // enabled by default in run script
            forPreprocessor:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_STRING_FLOW_ANALYSIS",
            value:true, //enabled by default in run script
            cli:true,
            webUI:true,
            name:"disable-reflection-string-flow-analysis",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"ANALYZE_REFLECTION_SUBSTRINGS",
            value:true, // enabled by default in run script
            cli:true,
            webUI:true,
            name:"disable-reflection-substring-analysis",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"MERGE_FIELD_AND_METHOD_SUBSTRINGS",
            value:true, // enabled by default in run script
            cli:true,
            webUI:true,
            name:"disable-merge-member-reflection-constants",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"MERGE_STRING_BUFFERS",
            value:true, //enabled by default in run script
            cli:true,
            webUI:true,
            name:"disable-merge-string-buffers",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<String>(
            id:"INCLUDE_IMPLICITLY_REACHABLE_CODE",
            value:true,  // enabled by default in run script
            webUI: true,
            cli: true,
            name:"exclude-implicitly-reachable-code",
            forPreprocessor:true,
            isAdvanced: true
        ),
        new AnalysisOption<Boolean>(
            id:"PADDLE_COMPAT",
            value:false,
            cli:true,
            webUI:true,
            name:"paddle-compat",
            forPreprocessor: true,
            isAdvanced:true
        ),        
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_FILTER",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exceptions-filter",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_ORDER",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exceptions-order",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_RANGE",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exceptions-range",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_CS",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exceptions-cs",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"DISABLE_REFLECTION",
            value:false,
            cli:true,
            webUI:true,
            name:"disable-reflection",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"CONTEXT_SENSITIVE_REFLECTION",
            value:false,
            cli:true,
            webUI:true,
            name:"context-sensitive-reflection",
            forPreprocessor:true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"CLIENT_EXCEPTION_FLOW",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exception-flow",
            forPreprocessor:true,
            isAdvanced:true
        ),        
        new AnalysisOption<Boolean>(
            id:"USE_BASED_REFLECTION_ANALYSIS",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-use-based-reflection-analysis",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"INVENT_UNKNOWN_REFLECTIVE_OBJECTS",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-invention-of-unknown-reflective-objects",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"REFINED_REFLECTION_OBJECTS",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-refined-reflection-objects",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"NO_CONTEXT_REPEAT",
            value:false,
            cli:true,
            webUI:true,
            name:"no-context-repeat",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"TRANSFORM_INPUT",
            description:"Transform input by removing redundant instructions.",
            value:false,
            cli:true,
            webUI:true,
            name:"transform-input",
            forPreprocessor: true,
            isAdvanced:true
        ),
        /* End of preprocessor normal flags] */

        
        /* Start of preprocessor exception flags] */        
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_PRECISE",
            value:true, // enabled by default in run script
            forPreprocessor:true,
            flagType:PreprocessorFlag.EXCEPTION_FLAG
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_IMPRECISE",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-imprecise-exceptions",
            forPreprocessor: true,
            isAdvanced:true,
            flagType:PreprocessorFlag.EXCEPTION_FLAG
        ),
        new AnalysisOption<Boolean>(
            id:"SEPARATE_EXCEPTION_OBJECTS",
            value:false,
            forPreprocessor:true,
            flagType:PreprocessorFlag.EXCEPTION_FLAG
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_EXPERIMENTAL",
            value:false,
            cli:true,
            webUI:true,
            name:"enable-exceptions-experimental",
            forPreprocessor:true,
            isAdvanced:true,
            flagType:PreprocessorFlag.EXCEPTION_FLAG
        ),
        /* End of preprocessor exception flags] */        

        
        //other options/flags
        new AnalysisOption<Boolean>(
            id:"DISABLE_PRECISE_EXCEPTIONS",
            value:false,
            cli:true,
            webUI:true,
            name:"disable-precise-exceptions",
            forPreprocessor:false,
            isAdvanced:true
        ),        
        new AnalysisOption<Boolean>(
            id:"DISABLE_MERGE_EXCEPTIONS",
            value:false,
            cli:true,
            webUI:true,
            name:"disable-merge-exceptions",
            forPreprocessor:false,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"SET_BASED",
            value:false,
        ),
        new AnalysisOption<Boolean>(
            id:"CSV",
            value:false,
        ),
        new AnalysisOption<Boolean>(
            id:"REFINE",
            value:false
        ),
        new AnalysisOption<Boolean>(
            id:"SSA",
            description: 'Use ssa transformation for input.',
            value:false,
            webUI:true,
            cli:true,
            name: "ssa"
        ),
        new AnalysisOption<Boolean>(
            id:"CACHE",
            description:"The analysis will use the cached facts, if they exist.",
            value:false,
            cli:true,
            webUI:true,
            name: "cache"
        ),
        new AnalysisOption<Boolean>(
            id:"STATS",
            description:"Load additional logic for collecting statistics.",
            value:false,
            cli:true,
            webUI:true,
            name: "full-stats"
        ),
        new AnalysisOption<Boolean>(
            id:"SANITY",
            description:"Load additional logic for sanity checks.",
            value:false,
            cli:true,
            webUI:true,
            name: "sanity"
        ),
        new AnalysisOption<Boolean>(
            id:"MEMLOG",
            value:false,
            cli:true,
            webUI:true,
            name: "log-mem-stats",
            isAdvanced: true
        ),
        new AnalysisOption<Boolean>(
            id:"RUN_JPHANTOM",
            description: 'Run jphantom for non-existent referenced jars.',
            value:false,
            webUI:true,
            cli:true,
            name: "run-jphantom"
        ),
        new AnalysisOption<Boolean>(
            id:"AVERROES",
            description: 'Use averroes tool to create a placeholder library.',
            value:false,
            webUI:true,
            cli:true,
            name: "averroes"
        ),
        new AnalysisOption<Boolean>(
            id:"DACAPO",
            value:false,
            webUI:true,
            cli:true,
            name:"dacapo",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"DACAPO_BACH",
            value:false,
            webUI:true,
            cli:true,
            name:"dacapo-bach",
            forPreprocessor: true,
            isAdvanced:true
        ),
        new AnalysisOption<String>(
            id:"DACAPO_BENCHMARK",
            value:null,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"DACAPO_2009",
            value:false,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"USE_ORIGINAL_NAMES",
            value:false,
            webUI:true,
            cli:true,
            name:"use-original-names",
        ),
        new AnalysisOption<Boolean>(
            id:"KEEP_LINE_NUMBER",
            value:false,
            webUI:true,
            cli:true,
            name:"keep-line-number",
        ),
        new AnalysisOption<String>( //Generates the properly named JRE option at runtime
            id:"JRE",
            description:"One of 1.3, 1.4, 1.5, 1.6, 1.7, system (default: system).",
            value:"system",
            webUI:true,
            cli:true,
            name:"jre",
            argName:"VERSION"
        ),
        new AnalysisOption<OS>(
            id:"OS",
            value:OS.OS_UNIX
        ),
        new AnalysisOption<Boolean>(
            id:"INCREMENTAL",
            value:false,
            webUI:true,
            cli:true,
            name:"incremental",
            isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"INTERACTIVE",
            value:false,
            name:"INTERACTIVE",
            isAdvanced:true
        ),
        //addtional options
        new AnalysisOption<String>(
            id:"APP_REGEX",
            description:"A regex expression for the Java package names to be analyzed.",
            value:null,
            webUI:true,
            cli:true,
            name:"regex",
            argName:"regex-expression"
        ),
    ]

    static final List<String> OPTIONS_EXCLUDED_FROM_ID_GENERATION = [
        "LOGICBLOX_HOME",
        "LD_LIBRARY_PATH",
        "BLOXBATCH",
        "BLOX_OPTS",
        "OS",
        "INCREMENTAL",
        "INTERACTIVE",
        "CACHE"
    ]

    // Not the best pattern, but limits the source code size :)
    static String doopHome
    static String doopLogic
    static String doopOut
    static String doopInputCache

    /**
     * Initializes Doop.
     * @param homePath The doop home directory (sets the doopHome variable, required).
     * @param outPath  The doop out directory (sets the doopOut variable, optional, defaults to doopHome/out).
     */
    static void initDoop(String homePath, String outPath) {

        //Check doopHome
        doopHome = homePath
        Helper.checkDirectoryOrThrowException(doopHome, "DOOP_HOME environment variable is invalid: $doopHome ")

        doopLogic = "$doopHome/logic"

        if (outPath) {
            doopOut = outPath
        }
        else {
            doopOut = "$doopHome/out"
        }

        //create all necessary files/folders
        File f = new File(doopOut)
        f.mkdirs()
        Helper.checkDirectoryOrThrowException(f, "Could not create ouput directory: $doopOut ")
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
     * Creates the analysis options by overriding the default options with the ones contained in the given properties.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param props - the properties.
     * @param filter - optional filter to apply before setting the option.
     * @return the default analysis options overridden by the values contained in the properties.
     */
    static Map<String, AnalysisOption> overrideDefaultOptionsWithProperties(Properties properties, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = createDefaultAnalysisOptions()
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
            option.value = property.split(",").collect { String s -> s.trim() }
        } else if (option.argName) {
            option.value = property
        } else {
            option.value = Boolean.parseBoolean(property)
        }
    }

    /**
     * Creates the analysis options by overriding the default options with the ones contained in the given CLI options.
     * An option is set only if filtered (the supplied filter returns true for the option).
     * @param cli - the CLI option accessor.
     * @param filter - optional filter to apply before setting the option.
     * @return the default analysis options overridden by the values contained in the CLI option accessor.
     */
    static Map<String, AnalysisOption> overrideDefaultOptionWithCLI(OptionAccessor cli, Closure<Boolean> filter) {
        Map<String, AnalysisOption> options = createDefaultAnalysisOptions()
        options.values().each { AnalysisOption option ->
            String optionName = option.name
            if (optionName) {
                def optionValue = cli[(optionName)]
                Logger.getRootLogger().debug "Processing $optionName = $optionValue"
                if (optionValue) { //Only true-ish values are of interest (false or null values are ignored)
                    boolean filtered = filter ? filter.call(option) : true
                    if (filtered) {
                        setOptionFromCLI(option, cli)
                    }
                }
            }
        }
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
        if (option.id == "DYNAMIC") {
            //Obscure cli builder feature: to get the value of a cl option as a List, you need to append an s
            //to its short name (the short name of the DYNAMIC option is d, so we invoke ds)
            option.value = cli.ds
        } else if (option.argName) {
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
