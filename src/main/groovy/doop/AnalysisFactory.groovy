package doop

import doop.preprocess.CppPreprocessor
import doop.preprocess.JcppPreprocessor
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A Factory for creating Analysis objects.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 31/8/2014
 */
class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())

    /**
     * Creates a new analysis, verifying the correctness of its name, jars and options
     */
    Analysis newAnalysis(String name, List<String> jars, Map<String, AnalysisOption> options) {

        //Verify that the name of the analysis is valid
        checkName(name)

        //TODO: Generate analysis id - for now id = name
        String id = name

        Analysis analysis = new Analysis(
            id     : id,
            name   : name,
            outDir : "${Doop.doopOut}/$name",
            preprocessor: (options.USE_JAVA_CPP.value ? new JcppPreprocessor() : new CppPreprocessor()),
            options: options
        )

        //Verify that the jars exist
        checkJars(analysis, jars)

        //check the JRE version
        checkJRE(analysis)

        //check the OS
        checkOS(analysis)

        //check app regex
        checkAppRegex(analysis)

        //check LogicBlox
        checkLogicBlox(analysis)

        //init external commands environment
        initExternalCommandsEnvironment(analysis)

        //TODO: check & verify other options

        //Create the outDir if required
        File f = new File(analysis.outDir)
        f.mkdirs()
        Helper.checkDirectoryOrThrowException(analysis.outDir, "Could not create analysis directory: ${analysis.outDir}")

        return analysis
    }

    /**
     * Verifies that the analysis, given by its name, exists
     */
    protected void checkName(String name) {
        logger.debug "Verifying analysis name: $name"
        String analysisPath = "${Doop.doopLogic}/${name}/analysis.logic"
        Helper.checkFileOrThrowException(analysisPath, "Unsupported analysis: $name")
    }

    /**
     * Given the list of jars (as filenames), the method validates that the jars exist and adds them as a List<File> in
     * the analysis
     */
    protected void checkJars(Analysis analysis, List<String> jars) {
        logger.debug "Verifying input jars: $jars"
        analysis.jars = jars.collect { String jar ->
            Helper.checkFileOrThrowException(jar, "Invalid jar: $jar")
        }
    }

    /**
     * Checks the JRE version and injects the appropriate JRE option in the analysis (as expected by the preprocessor
     * logic)
     */
    protected void checkJRE(Analysis analysis) {

        JRE jreVersion
        String jreValue = analysis.options.JRE.value

        logger.debug "Verifying JRE version: $jreValue"

        switch(jreValue) {
            case "1.3":
                jreVersion = JRE.JRE13
                break
            case "1.4":
                jreVersion = JRE.JRE14
                break
            case "1.5":
                jreVersion = JRE.JRE15
                break
            case "1.6":
                jreVersion = JRE.JRE16
                break
            case "1.7":
                jreVersion = JRE.JRE17
                break
            case "system":
                String version = System.getProperty("java.class.version")
                if (version.startsWith("51")) {
                    jreVersion = JRE.JRE17
                    break
                }
                else if (version.startsWith("50")) {
                    jreVersion = JRE.JRE16
                    break
                }
                else if (version.startsWith("49")) {
                    jreVersion = JRE.JRE15
                    break
                }
                else if (version.startsWith("48")) {
                    jreVersion = JRE.JRE14
                    break
                }
                else if (version.startsWith("47")) {
                    jreVersion = JRE.JRE13
                    break
                }
                else {
                    throw new RuntimeException("Unsupported Java major version: $version")
                }
            default:
                throw new RuntimeException("Invalid JRE version: $jreValue")
        }

        //sanity check
        EnumSet<JRE> supportedValues = EnumSet.allOf(JRE)
        if (! (jreVersion in supportedValues)) {
            throw new RuntimeException("Unsupported JRE version: $jreVersion")
        }

        //generate the JRE constant for preprocessor
        AnalysisOption<Boolean> jreOption = new AnalysisOption<>(
                id:jreVersion.name(),
                value:true,
                forPreprocessor: true
        )
        analysis.options[(jreOption.id)] = jreOption
    }

    //not used
    private static void checkOS(Analysis analysis) {
        //TODO: For now it is always OS.OS_UNIX (the default)

        OS os = analysis.options.OS.value as OS

        //sanity check
        EnumSet<OS> supportedValues = EnumSet.allOf(OS)
        if (! (os in supportedValues)) {
            throw new RuntimeException("Unsupported OS: $os")
        }

        //generate the OS constant for preprocessor
        AnalysisOption<Boolean> osOption = new AnalysisOption<>(
                id:os.name(),
                value:true,
                forPreprocessor: true
        )
        analysis.options[(osOption.id)] = osOption
    }

    /**
     * If an app regex is not present, it generates one
     */
    protected void checkAppRegex(Analysis analysis) {
        if (!analysis.options.APP_REGEX.value) {
            logger.debug "Generating an app regex"
            analysis.options.APP_REGEX.value = generateAppRegex(analysis.jars)
        }
    }

    /**
     * Verifies the correctness of the LogicBlox related options
     */
    protected void checkLogicBlox(Analysis analysis) {

        AnalysisOption lbhome = analysis.options.LOGICBLOX_HOME
        String lbHomePath = lbhome.value

        logger.debug "Verifying LogicBlox home: $lbHomePath"

        File lbHomeJavaFile = Helper.checkDirectoryOrThrowException(lbHomePath, "The ${lbhome.name} value is invalid: ${lbhome.value}")

        analysis.options.LD_LIBRARY_PATH.value = lbHomeJavaFile.getAbsolutePath() + "/bin"
        String bloxbatch = lbHomeJavaFile.getAbsolutePath() + "/bin/bloxbatch"
        Helper.checkFileOrThrowException(bloxbatch, "The bloxbatch file is invalid: $bloxbatch")
        analysis.options.BLOXBATCH.value = bloxbatch
    }


    /**
     * Initializes the external commands environment, by:
     * <ul>
     *     <li>adding the LD_LIBRARY_PATH option to the current environment
     *     <li>modifying PATH to also include the LD_LIBRARY_PATH option
     *     <li>adding the value of the LOGICBLOX_HOME option to the current environment
     * </ul>
     */
    protected Map<String, String> initExternalCommandsEnvironment(Analysis analysis) {

        logger.debug "Initializing the environment of the external commands"

        AnalysisOption ldLibraryPath = analysis.options.LD_LIBRARY_PATH
        Map<String, String> env = [:]
        env.putAll(System.getenv())
        String path = env.PATH
        if (path) {
            path = "$path${File.pathSeparator}${ldLibraryPath.value}"
        }
        else {
            path = "${ldLibraryPath.value}"
        }
        env.PATH = path
        env.LD_LIBRARY_PATH = ldLibraryPath.value
        env.LOGICBLOX_HOME = analysis.options.LOGICBLOX_HOME.value

        analysis.externalCommandsEnvironment = env
    }

    /*
    Generates an app regex using the input jars
     */
    private static String generateAppRegex(List<File> jars) {
        Set excluded = ["*", "**"] as Set
        jars.drop(1).each { File jar ->
            excluded += getPackages(jar)
        }

        Set<String> packages = getPackages(jars[0]) - excluded
        //println "Excluded: $excluded"
        //println "Packages: $packages"

        return packages.join(':')
    }

    /*
    Returns a set of the packages contained in the given jar
     */
    private static Set<String> getPackages(File jar) {

        ZipFile zip = new ZipFile(jar)
        Enumeration<? extends ZipEntry> entries = zip.entries()
        List<String> packages = entries?.findAll { ZipEntry entry ->
            entry.getName().endsWith(".class")
        }.collect { ZipEntry entry ->
            FilenameUtils.getPath(entry.getName()).replace('/' as char, '.' as char) + '*'
        }
        return (packages as Set)
    }
}
