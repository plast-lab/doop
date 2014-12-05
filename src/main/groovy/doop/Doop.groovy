package doop
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
            description: "set the path to LogicBlox home (default: the value of the LOGICBLOX_HOME environment variable)",
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
            description:'Specify the main class',
            value:null,
            webUI: true,
            cli:true,
            name: "main",
            argName: 'mainClass'
        ),
        new AnalysisOption<List<String>>(
            id:"DYNAMIC",
            description:"File with tab-separated data for Config:DynamicClass. Separate multiple files with a comma",
            value:[],
            webUI:true,
            cli:true,
            name:"dynamic",
            argName:"FILE",
            isFile:true
        ),
        new AnalysisOption<String>(
            id:"TAMIFLEX",
            description:"File with tamiflex data",
            value:null,
            webUI:true,
            cli:true,
            name:"tamiflex",
            argName:"FILE",
            isFile:true
        ),
        new AnalysisOption<String>(
            id:"CLIENT_CODE",
            description:"Additional directory/file of client analysis to include",
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
        //LATEST: NO_MODELING_OF_NUMS_OR_NULL
        new AnalysisOption<String>(
            id:"NO_MODELING_OF_NUMS_OR_NULL",
            value:true, // enabled by default in run script
            forPreprocessor:true
        ),
        //LATEST: INCLUDE_IMPLICITLY_REACHABLE_CODE
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
            id:"DISABLE_PRECISE_EXCEPTIONS",
            value:false,
            cli:true,
            webUI:true,
            name:"disable-precise-exceptions",
            forPreprocessor:false,
			isAdvanced:true
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
            id:"DISABLE_MERGE_EXCEPTIONS",
            value:false,
            cli:true,
            webUI:true,
            name:"disable-merge-exceptions",
            forPreprocessor:false,
			isAdvanced:true
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_PRECISE",
            value:true, // enabled by default in run script
            forPreprocessor:true,
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
            id:"DISTINGUISH_NO_STRING_CONSTANTS",
            value:false,
            cli:true,
            webUI:true,
            name:"distinguish-no-string-constants",
            forPreprocessor: true,
			isAdvanced:true,
			flagType:PreprocessorFlag.CONSTANT_FLAG
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
        //other options/flags
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
            description:"The analysis will use the cached input relations, if such exist",
            value:false,
            cli:true,
            webUI:true,
            name: "cache"
        ),
        new AnalysisOption<Boolean>(
            id:"STATS",
            description:"Load additional logic for collecting statistics",
            value:false,
            cli:true,
            webUI:true,
            name: "full-stats"
        ),
        new AnalysisOption<Boolean>(
            id:"SANITY",
            description:"Load additional logic for sanity checks",
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
            id:"SOLO",
            value:false,
            cli:true,
            name: "solo-run"
        ),
        new AnalysisOption<Boolean>(
            id:"ALLOW_PHANTOM",
            description: 'Allow non-existent referenced jars',
            value:false,
            webUI:true,
            cli:true,
            name: "allow-phantom"
        ),
        new AnalysisOption<Boolean>(
            id:"AVERROES",
            description: 'Use averroes tool to create a placeholder library',
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
        new AnalysisOption<String>( //Generates the properly named JRE option at runtime
            id:"JRE",
            description:"One of 1.3, 1.4, 1.5, 1.6, 1.7, system (default: system)",
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
            id:"COLOR",
            value:false,
            name:"color",
			isAdvanced:true
        ),
		new AnalysisOption<Boolean>(
            id:"INTERACTIVE",
            value:false,
            name:"INTERACTIVE",
			isAdvanced:true
        ),
        //jdoop-specific options
        new AnalysisOption<String>(
            id:"APP_REGEX",
            description:"A regular expression for the Java package names to be analyzed",
            value:null,
            webUI:true,
            cli:true,
            name:"regex",
            argName:"regular-expression"
        ),
        new AnalysisOption<String>(
            id:"USE_JAVA_CPP",
            description:"Use a full-java preprocessor for the logic files",
            value:false,
            webUI:true,
            cli:true,
            name:"jcpp",
			isAdvanced:true
        ),
    ]

    //Not the best pattern, but limits the source code size :)
    static String doopHome
    static String doopLogic
    static String doopOut

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
        Helper.checkDirectoryOrThrowException(doopOut, "Could not create ouput directory: $doopOut ")
    }

    /**
     * Creates the default analysis options.
     * @return Map<String, AnalysisOptions>.
     */
    static Map<String, AnalysisOption> createDefaultAnalysisOptions() {
        Map<String, AnalysisOption> options = [:]
        ANALYSIS_OPTIONS.each { AnalysisOption option -> options[(option.id)] = option }
        return options
    }

    /**
     * Creates the analysis options.
     * This method checks for a doop.properties file: (a) in the user's home directory or (b) in the Doop home
     * directory and if such a file is present, its options will override the default ones.
     * @return Map<String, AnalysisOptions>.
     */
    static Map<String, AnalysisOption> createAnalysisOptions() {
        Map<String, AnalysisOption> options = createDefaultAnalysisOptions()

        Properties props = new Properties()

        def candidates = [System.getProperty("user.home") + "/doop.properties", "$doopHome/doop.properties"]
        for (c in candidates) {
            try {
                File f = Helper.checkFileOrThrowException(c, "Not a valid file: $c")
                f.withReader { Reader r -> props.load(r)}
                overrideAnalysisOptionsFromProperties(options, props)
                break
            }
            catch (e) {
                // do nothing
            }
        }

        return options

    }

    /**
     * Overrides the given analysis options with the ones contained in the given properties.
     * This method provides special handling for the DYNAMIC option, in order to support multiple values for it.
     * @param options - the options to be overridden
     * @param properties - the properties to use
     */
    static void overrideAnalysisOptionsFromProperties(Map<String, AnalysisOption> options, Properties properties) {
        if (properties.size() > 0) {
            properties.each { Map.Entry<String, String> entry->
                AnalysisOption option = options.get(entry.key)
                if (option && entry.value && entry.value.trim().length() > 0) {
                    if (option.id == "DYNAMIC") {
                        option.value = entry.value.split(",").collect{ String s -> s.trim() }
                    }
                    else if (option.argName) {
                        option.value = entry.value
                    }
                    else {
                        option.value = Boolean.parseBoolean(entry.value)
                    }
                }
            }
        }
    }

}
