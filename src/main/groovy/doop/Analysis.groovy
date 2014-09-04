package doop

import doop.preprocess.Preprocessor
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 *
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 */
class Analysis implements Runnable {

    protected Log logger = LogFactory.getLog(getClass())

    /**
     * The unique identifier of the analysis (that determines the caching)
     */
    String id

    /**
     * The name of the analysis (that determines the logic)
     */
    String name

    /**
     * The output dir of the analysis (results and intermediate files)
     */
    String outDir

    /**
     * The options of the analysis
     */
    Map<String, AnalysisOption> options

    /**
     * The jar files of the analysis
     */
    List<File> jars

    /**
     * The environment for running external commands
     */
    Map<String, String> externalCommandsEnvironment

    /**
     * The preprocessor for the logic files of the analysis
     */
    Preprocessor preprocessor

    String inputFilesChecksum
    String logicFilesChecksum

    protected Analysis() {}

    /**
     * Runs the analysis.
     */
    @Override
    void run() {
        Helper.execWithTiming (logger) {
            preprocessLogic()
            createDatabase()
        }
    }

    /**
     * Precprocess the logic files of the analysis.
     * Mimics the behavior of the init-analysis function of the doop run script.
     */
    protected void preprocessLogic() {

        logger.info "Pre-processing the logic files"

        String basePath = "${Doop.doopLogic}/${name}"
        String baseLibPath = "${Doop.doopLogic}/library"
        String baseClientPath = "${Doop.doopLogic}/client"

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            //process the dacapo logic
        }
        else {
            preprocessor.preprocess(this, basePath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            preprocessor.preprocess(this, basePath, "delta.logic",        "${outDir}/${name}-delta.logic")
            preprocessor.preprocess(this, basePath, "analysis.logic",     "${outDir}/${name}.logic")
        }

        preprocessor.preprocess(this, baseLibPath,    "reflection-delta.logic",     "${outDir}/reflection-delta.logic")
        preprocessor.preprocess(this, baseClientPath, "exception-flow-delta.logic", "${outDir}/exception-flow-delta.logic")
        preprocessor.preprocess(this, baseClientPath, "auxiliary-heap-allocations-delta.logic",
                        "${outDir}/auxiliary-heap-allocations-delta.logic"
        )
    }

    /**
     * Creates the lb database.
     * Mimics the behavior of the create-database function of the doop run script.
     */
    protected void createDatabase() {

        String factsDir = "$outDir/facts"
        logger.info "Creating facts directory: $factsDir"
        new File(factsDir).mkdirs()

        //TODO: support caching

        dealWithPhantomRefs()

        //TODO: run averroes, if specified

        sootGenerateFacts(factsDir)

        String dbDir = "$outDir/database"
        new File(dbDir).mkdirs()

        initDatabase(dbDir)

    }


    /**
     * Runs jphantom if phantom refs are not allowed
     */
    protected void dealWithPhantomRefs(){
        if (!options.ALLOW_PHANTOM.value) {

            logger.info "Running jphantom to generate complement jar"

            String jar = jars[0]
            String jarName = FilenameUtils.getBaseName(jar)
            String jarExt = FilenameUtils.getExtension(jar)
            String newJar = "${jarName}-complemented.${jarExt}"
            String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
            logger.debug "Params of jphantom: ${params.join(' ')}"

            //we invoke the main method reflectively to avoid adding jphantom as a compile-time dependency
            ClassLoader loader = phantomClassLoader()
            Helper.execJava(loader, "jphantom.Driver", params)

            //set the jar of the analysis to the complemented one
            jars[0] = "$outDir/$newJar"
        }
    }

    /**
     * soot fact generation
     */
    protected void sootGenerateFacts(String factsDir) {

        logger.info "Running soot to generate facts"

        List<String> deps = jars.drop(1)
        List<String> jreLinks = jreLinkArgs()
        List<String> depArgs = (deps + jreLinks).collect { String arg -> ["-l", arg] }.flatten()

        String[] params = ["-full"] + depArgs + ["-application-regex", options.APP_REGEX.value]

        if (options.SSA.value) {
            params = params + ["-ssa"]
        }

        if (options.ALLOW_PHANTOM.value) {
            params = params + ["-allow-phantom"]
        }

        if (options.MAIN_CLASS.value) {
            params = params + ["-main", options.MAIN_CLASS.value]
        }

        params = params + ["-d", factsDir, jars[0]]

        logger.debug "Params of soot: ${params.join(' ')}"

        //we invoke the main method reflectively to avoid adding soot as a compile-time dependency
        ClassLoader loader = sootClassLoader()
        Helper.execJava(loader, "Main", params)
    }



    protected void initDatabase(String dbDir) {

        String bloxbatch = options.BLOXBATCH.value

        Helper.execWithTiming(logger) {
            logger.info "Creating database in $dbDir"
            String command = "$bloxbatch -db $dbDir -create -overwrite -blocks base"
            logger.debug command
            Helper.execCommand(command, externalCommandsEnvironment)
        }

        Helper.execWithTiming(logger) {
            logger.info "Loading fact declarations"
            String command = "$bloxbatch -db $dbDir -addBlock -file ${Doop.doopHome}/logic/library/fact-declarations.logic"
            logger.debug command
            Helper.execCommand(command, externalCommandsEnvironment)
        }
    }

    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:name, outDir:outDir].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.join("\n")
    }

    /**
     * Creates a new class loader for running jphantom
     */
    private static ClassLoader phantomClassLoader() {
        //TODO: for now, we hard-code the jphantom jar
        String jphantom = "${Doop.doopHome}/lib/jphantom-1.0-jar-with-dependencies.jar"
        File f = Helper.checkFileOrThrowException(jphantom, "jphantom jar missing or invalid: $jphantom")
        URL[] classpath = [f.toURI().toURL()]
        return new URLClassLoader(classpath)
    }

    /**
     * Creates a new class loader for running soot
     */
    private static ClassLoader sootClassLoader() {
        //TODO: for now, we hard-code the soot jars
        String sootClasses = "${Doop.doopHome}/lib/sootclasses-2.5.0.jar"
        String sootFactGeneration = "${Doop.doopHome}/lib/soot-fact-generation.jar"

        File f1 = Helper.checkFileOrThrowException(sootClasses, "soot classes jar missing or invalid: $sootClasses")
        File f2 = Helper.checkFileOrThrowException(sootFactGeneration, "soot fact generation jar missing or invalid: $sootFactGeneration")

        URL[] classpath = [f1.toURI().toURL(), f2.toURI().toURL()]
        return new URLClassLoader(classpath)
    }

    /**
     * Generates a list of the jre link arguments for soot
     */
    private List<String> jreLinkArgs() {

        //TODO: deal with JRE versions other than system

        if (options.JRE.value == "system") {
            String javaHome = System.getProperty("java.home")
            return ["$javaHome/lib/rt.jar", "$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
        }
        else {
            throw new RuntimeException("Only system JRE is currently supported")
        }
    }


}
