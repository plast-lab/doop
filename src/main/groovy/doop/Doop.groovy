package doop

import org.apache.log4j.DailyRollingFileAppender
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 *
 * Global DOOP configuration, logging and bootstrapping
 */
class Doop {

    private static final String DOOP = "doop"

    private static final List<AnalysisOption> ANALYSIS_OPTIONS = [
        //LOGICBLOX related options
        new AnalysisOption<String>(
            name:"LOGICBLOX_HOME",
            value:null
        ),
        new AnalysisOption<String>(
            name:"LD_LIBRARY_PATH",
            value:null
        ),
        new AnalysisOption<String>(
            name:"bloxbatch",
            value:null
        ),
        new AnalysisOption<String>(
            name:"BLOX_OPTS",
            value:null
        ),
        //Main options
        new AnalysisOption<String> (
            name:"JAR",
            value:null
        ),
        new AnalysisOption<String>(
            name:"MAIN_CLASS",
            value:null,
            cli: true,
            cliName: "main"
        ),
        new AnalysisOption<String>(
            name:"DYNAMICS",
            value:null
        ),
        new AnalysisOption<String>(
            name:"TAMIFLEX",
            value:null
        ),
        new AnalysisOption<String>(
            name:"CLIENT_CODE",
            value:null
        ),
        //Misc options
        new AnalysisOption<JRE>(
            name:"JRE",
            value:JRE.SYSTEM  //Generates options at runtime
        ),
        new AnalysisOption<JRE>(
            name:"OS",
            value:OS.OS_UNIX  //Generates options at runtime
        ),
        new AnalysisOption<Boolean>(
            name:"ALLOW_PHANTOM",
            value:true,
            cli:true,
            cliName: "allow-phantom"
        ),
        //Other preprocessor options
        new AnalysisOption<Boolean>(
            name:"DACAPO",
            value:false,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"DACAPO_BACH",
            value:false,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"DISTINGUISH_REFLECTION_STRING_CONSTANTS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"REFLECTION_STRING_FLOW_ANALYSIS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"ANALYZE_REFLECTION_SUBSTRINGS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"MERGE_FIELD_AND_METHOD_SUBSTRINGS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"MERGE_STRING_BUFFERS",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"EXCEPTIONS_PRECISE",
            value:true,
            forPreprocessor: true
        ),
        new AnalysisOption<Boolean>(
            name:"PADDLE_COMPAT",
            value:false,
            forPreprocessor: true
        )
    ]

    static final String DOOP_HOME = System.getenv("DOOP_HOME")

    static final String LOGIC_PATH = "$DOOP_HOME/logic"
    static final String OUTPUT_PATH = "$DOOP_HOME/out"


    //Public methods
    static void bootstrap() {
        //check DOOP_HOME
        if (!DOOP_HOME) {
            throw new RuntimeException("DOOP_HOME environment variable is not set")
        }
        File f = new File(DOOP_HOME)
        if (!f.exists() || !f.isDirectory()) {
            throw new RuntimeException("DOOP_HOME environment variable is invalid: $DOOP_HOME")
        }

        //initialize logging
        initLogging("DEBUG", "${DOOP_HOME}/logs")

        //create all necessary files/folders
        f = new File(OUTPUT_PATH)
        f.mkdirs()
        if (!f.exists() || !f.isDirectory()) {
            throw new RuntimeException("Could not create ouput directory: $OUTPUT_PATH")
        }
    }

    static Analysis newAnalysis(String name, Map<String, AnalysisOption> options) {

        //Verify that the name of the analysis is valid
        String analysisPath = "$LOGIC_PATH/${name}/analysis.logic"
        File f = new File(analysisPath)
        if (!f.exists() || !f.isFile()) {
            throw new RuntimeException("Unsupported analysis: $name")
        }

        //TODO: Generate analysis id - for now id = name
        String id = name

        Analysis analysis = new Analysis(
            id     : id,
            name   : name,
            outDir : "$OUTPUT_PATH/$name",
            options: options
        )

        //check the JRE version
        checkJRE(analysis)

        //TODO: check the OS
        checkOS(analysis)


        //TODO: check & verify the other options

        //Create the outDir if required
        f = new File(analysis.outDir)
        f.mkdirs()
        if(!f.exists() || !f.isDirectory()) {
            throw new RuntimeException("Could not create analysis folder: ${analysis.outDir}")
        }

        return analysis
    }

    static Map<String, AnalysisOption> createDefaultAnalysisOptions() {
        Map<String, AnalysisOption> options = [:]
        ANALYSIS_OPTIONS.each { AnalysisOption option -> options[(option.name)] = option }
        return options
    }

    //Helper methods
    private static void initLogging(String logLevel, String logDir) {
        File dir = new File(logDir)
        if (!dir.exists()) dir.mkdir();

        String logFile =  "${logDir}/${DOOP}.log"

        PatternLayout layout = new PatternLayout("%d [%t] %-5p %c - %m%n");
        Logger root = Logger.getRootLogger();
        root.setLevel(Level.toLevel(logLevel, Level.WARN));
        DailyRollingFileAppender appender = new DailyRollingFileAppender(layout, logFile, "'.'yyyy-MM-dd");
        root.addAppender(appender);
    }

    private static void checkJRE(Analysis analysis) {

        JRE jreVersion

        if (analysis.options.JRE.value == JRE.SYSTEM) {
            String version = System.getProperty("java.class.version")
            if (version.startsWith("51")) {
                jreVersion = JRE.JRE17
            }
            else if (version.startsWith("50")) {
                jreVersion = JRE.JRE16
            }
            else if (version.startsWith("49")) {
                jreVersion = JRE.JRE15
            }
            else if (version.startsWith("48")) {
                jreVersion = JRE.JRE14
            }
            else if (version.startsWith("47")) {
                jreVersion = JRE.JRE13
            }
            else {
                throw new RuntimeException("Unsupported Java major version: $version")
            }
        }
        else {
            jreVersion = analysis.options.JRE.value as JRE
        }

        //sanity check
        EnumSet<JRE> supportedValues = EnumSet.allOf(JRE)
        if (! (jreVersion in supportedValues)) {
            throw new RuntimeException("Unsupported JRE version: $jreVersion")
        }

        //generate the JRE constant for preprocessor
        AnalysisOption<Boolean> jreOption = new AnalysisOption<>(
            name:jreVersion.name(),
            value:true,
            forPreprocessor: true
        )
        analysis.options[(jreOption.name)] = jreOption
    }

    private static void checkOS(Analysis analysis) {
        //For now it is always OS.OS_UNIX (the default)

        OS os = analysis.options.OS.value as OS

        //sanity check
        EnumSet<OS> supportedValues = EnumSet.allOf(OS)
        if (! (os in supportedValues)) {
            throw new RuntimeException("Unsupported OS: $os")
        }

        //generate the OS constant for preprocessor
        AnalysisOption<Boolean> osOption = new AnalysisOption<>(
            name:os.name(),
            value:true,
            forPreprocessor: true
        )
        analysis.options[(osOption.name)] = osOption
    }
}
