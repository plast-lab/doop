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
            definedByUser:true,
            name:"lbhome",
            argName: "path"
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
            definedByUser: true,
            name: "main",
            argName: 'mainClass'
        ),
        new AnalysisOption<String>(
            id:"DYNAMICS",
            value:null
        ),
        new AnalysisOption<String>(
            id:"TAMIFLEX",
            value:null
        ),
        new AnalysisOption<String>(
            id:"CLIENT_CODE",
            value:null
        ),
        //Misc options
        new AnalysisOption<String>( //Generates the properly named JRE option at runtime
            id:"JRE",
            description:"One of 1.3, 1.4, 1.5, 1.6, 1.7, system (default: system)",
            value:"system",
            definedByUser:true,
            name:"jre",
            argName:"VERSION"
        ),
        new AnalysisOption<OS>(
            id:"OS",
            value:OS.OS_UNIX
        ),
        new AnalysisOption<Boolean>(
            id:"SSA",
            description: 'Use ssa transformation for input.',
            value:false,
            definedByUser:true,
            name: "ssa"
        ),
        new AnalysisOption<Boolean>(
            id:"ALLOW_PHANTOM",
            description: 'Allow non-existent referenced jars',
            value:false,
            definedByUser:true,
            name: "allow-phantom"
        ),
        new AnalysisOption<Boolean>(
            id:"AVERROES",
            description: 'Use averroes tool to create a placeholder library',
            value:false,
            definedByUser:true,
            name: "averroes"
        ),
        //Other preprocessor options
        new AnalysisOption<Boolean>(
            id:"DACAPO",
            value:false,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"DACAPO_BACH",
            value:false,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"DISTINGUISH_REFLECTION_STRING_CONSTANTS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"REFLECTION_STRING_FLOW_ANALYSIS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"ANALYZE_REFLECTION_SUBSTRINGS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"MERGE_FIELD_AND_METHOD_SUBSTRINGS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"MERGE_STRING_BUFFERS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"EXCEPTIONS_PRECISE",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            id:"PADDLE_COMPAT",
            value:false,
            forPreprocessor: true
        ),
        //jdoop-specific options
        new AnalysisOption<String>(
            id:"APP_REGEX",
            description:"A regular expression for the Java package names to be analyzed",
            value:null,
            definedByUser:true,
            name:"regex",
            argName:"regular-expression"
        ),
        new AnalysisOption<String>(
            id:"USE_JAVA_CPP",
            description:"Use a full-java preprocessor for the logic files",
            value:false,
            definedByUser:true,
            name:"jcpp"
        ),
    ]

    //Not the best pattern, but limits the source code size :)
    static String doopHome
    static String doopLogic
    static String doopOut


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

    static Map<String, AnalysisOption> createDefaultAnalysisOptions() {
        Map<String, AnalysisOption> options = [:]
        ANALYSIS_OPTIONS.each { AnalysisOption option -> options[(option.id)] = option }
        return options
    }

}
