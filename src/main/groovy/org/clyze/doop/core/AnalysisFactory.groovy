package org.clyze.doop.core

import groovy.transform.TypeChecked
import java.util.jar.Attributes
import java.util.jar.JarFile
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.input.DefaultInputResolutionContext
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.system.*

/**
 * A Factory for creating Analysis objects.
 *
 * All the methods invoked by newAnalysis (either directly or indirectly) could have been static helpers (e.g. entailed
 * in the Helper class) but they are protected instance methods to allow descendants to customize
 * all possible aspects of Analysis creation.
 */
@TypeChecked class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())
    static final char[] EXTRA_ID_CHARACTERS = '_-.'.toCharArray()
    static final String HASH_ALGO = "SHA-256"

    /**
     * A helper class that acts as an intermediate holder of the analysis variables.
     */
    protected static class AnalysisVars {
        String name
        Map<String, AnalysisOption> options
        Set<String> inputJars
        List<File> inputJarFiles
        List<String> jreJars

        @Override
        String toString() {
            return [
                name     : name,
                options  : options.values().toString(),
                inputJars: inputJars.toString(),
                inputJarFiles: inputJarFiles.toString(),
                jreJars  : jreJars.toString()
            ].toString()
        }
    }

    /**
     * Creates a new analysis, verifying the correctness of its id, name, options and inputJars using
     * the supplied input resolution mechanism.
     * If the supplied id is empty or null, an id will be generated automatically.
     * Otherwise the id will be validated:
     * - if it is valid, it will be used to identify the analysis,
     * - if it is invalid, an exception will be thrown.
     */
    Analysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        //Verify that the name of the analysis is valid
        checkName(name)

        AnalysisVars vars = new AnalysisVars(
            name     : name,
            options  : options,
            inputJars: context.inputs(),
            inputJarFiles: checkInputs(context),
            jreJars  : jreLinkArgs(options)
        )

        logger.debug vars

        //process the options
        processOptions(vars)

        //verify lb options
        checkLogicBlox(vars)

        //init the environment used for executing commands
        Map<String, String> commandsEnv = initExternalCommandsEnvironment(vars)

        //TODO: Create empty jar. Is it needed?

        //TODO: Check if input is given (incremental). Is it needed?
        checkDACAPO(vars)

        checkAppGlob(vars)

        //We don't need to renew the averroes properties file here (we do it in Analysis.runAverroes())

        //TODO: Add client code extensions into the main logic (/bin/weave-client-logic)

        String analysisId
        if (id) { //non-empty or null
            //validate and set the user supplied id
            analysisId = validateUserSuppliedId(id)
        }
        else {
            //Generate the id
            analysisId = generateID(vars)
        }
        String cacheId = generateCacheID(vars)

        //Create the outDir if required
        File outDir = createOutputDirectory(vars, analysisId)

        File cacheDir = new File("${Doop.doopCache}/$cacheId")

        Analysis analysis = new Analysis(
            analysisId,
            outDir.toString(),
            cacheDir.toString(),
            name,
            options,
            context,
            vars.inputJarFiles,
            vars.jreJars,
            commandsEnv
        )

        logger.debug "Created new analysis"
        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputJars using
     * the default input resolution mechanism.
     */
    Analysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, List<String> jars) {
        DefaultInputResolutionContext context = new DefaultInputResolutionContext()
        context.add(jars)
        return newAnalysis(id, name, options, context)
    }

    /**
     * Verifies that the analysis, given by its name, exists
     */
    protected void checkName(String name) {
        logger.debug "Verifying analysis name: $name"
        def analysisPath = "${Doop.analysesPath}/${name}/analysis.logic"
        FileOps.findFileOrThrow(analysisPath, "Unsupported analysis: $name")
    }

    protected String validateUserSuppliedId(String id) {
        String trimmed = id.trim()
        boolean isValid = trimmed.toCharArray().every { char c->
            Character.isLetter(c) || Character.isDigit(c) || c in EXTRA_ID_CHARACTERS
        }

        if (!isValid) {
            throw new RuntimeException("Invalid analysis id: $id. The id should contain only letters, digits, " +
                                       "${EXTRA_ID_CHARACTERS.collect{"'$it'"}.join(', ')}.")
        }
        return trimmed
    }

    /**
     * Creates the analysis output dir, if required.
     */
    protected File createOutputDirectory(AnalysisVars vars, String id) {
        def outDir = new File("${Doop.doopOut}/${vars.name}/${id}")
        outDir.mkdirs()
        FileOps.findDirOrThrow(outDir, "Could not create analysis directory: ${outDir}")
        return outDir
    }

    /**
     * Generates the analysis ID using all of its components (name, inputJars and options).
     */
    protected String generateID(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.keySet().findAll {
            !Doop.OPTIONS_EXCLUDED_FROM_ID_GENERATION.contains(it)
        }.collect {
            String option -> return vars.options.get(option).toString()
        }
        idComponents = [vars.name] + vars.inputJars + idComponents
        logger.debug("ID components: $idComponents")
        String id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    /**
     * Generates the cache ID (for input facts) using the needed components.
     */
    protected String generateCacheID(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.values().findAll {
            it.forCacheID
        }.collect {
            AnalysisOption option -> option.toString()
        }

        Collection<String> checksums = new File(Doop.factsPath).listFiles().collect {
            File file -> CheckSum.checksum(file, HASH_ALGO)
        }

        checksums += vars.inputJarFiles.collect {
            File file -> CheckSum.checksum(file, HASH_ALGO)
        }

        checksums += vars.jreJars.collect {
            String file -> CheckSum.checksum(new File(file), HASH_ALGO)
        }

        if(vars.options.TAMIFLEX.value) {
            checksums += [CheckSum.checksum(new File(vars.options.TAMIFLEX.value.toString()), HASH_ALGO)]
        }

        def checksumsFile = FileOps.findFileOrThrow("${Doop.doopHome}/checksums.properties", "Invalid checksums")
        Properties props = FileOps.loadProperties(checksumsFile)
        checksums += [props.getProperty(Doop.SOOT_CHECKSUM_KEY)]

        idComponents = checksums + idComponents

        logger.debug("Cache ID components: $idComponents")
        String id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    /**
     * Generates a list of the jre link arguments for soot
     */
    protected List<String> jreLinkArgs(Map<String, AnalysisOption> options) {

        String jre = options.JRE.value
        String path = "${options.JRE_LIB.value}/jre${jre}/lib"

        switch(jre) {
            case "1.3":
                return FileOps.findFiles(["${path}/rt.jar".toString()])
            case "1.4":
            case "1.5":
            case "1.6":
            case "1.7":
            case "1.8":
                return FileOps.findFiles(["${path}/rt.jar".toString(),
                                           "${path}/jce.jar".toString(),
                                           "${path}/jsse.jar".toString()])
            case "system":
                /*
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/rt.jar", "$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
                */
                return []
        }
    }

    /**
     * Given the list of analysis inputs, as Strings, the method validates that the inputs exist,
     * using the input resolution mechanism. It finally returns the inputs as a List<File>.
     */
    protected List<File> checkInputs(InputResolutionContext context) {
        Collection<String> inputs = context.inputs()
        logger.debug "Verifying analysis inputs: $inputs"
        if (!inputs) throw new RuntimeException("No inputs provided for the analysis")
        context.resolve()
        return context.getAll()
    }

    /**
     * Processes the options of the analysis.
     */
    protected void processOptions(AnalysisVars vars) {

        logger.debug "Processing analysis options"

        Map<String, AnalysisOption> options = vars.options

        /*
         * We mimic the checks of the run script for verifiability of this implementation,
         * even though the majority of checks are not required.
         */

        if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
        }

        if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.PADDLE_COMPAT.value = false
        } else {
            // Merging of method and field names happens only if we distinguish
            // reflection strings in the first place.
            options.REFLECTION_MERGE_MEMBER_CONSTANTS.value = false
        }

        if (options.DISTINGUISH_NO_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_NO_STRING_CONSTANTS.value = true
            options.PADDLE_COMPAT.value = false
        }

        if (!options.REFLECTION_STRING_FLOW_ANALYSIS.value) {
            // It makes no sense to analyze partial strings that may match fields
            // when we don't track the flow of these strings through StringBuilders.
            options.REFLECTION_SUBSTRING_ANALYSIS.value = false
        }

        if (options.ENABLE_REFLECTION_CLASSIC.value) {
            options.DISTINGUISH_NO_STRING_CONSTANTS.value = false
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.ENABLE_REFLECTION.value = true
            options.REFLECTION_MERGE_MEMBER_CONSTANTS.value = true
            options.REFLECTION_STRING_FLOW_ANALYSIS.value = true
            options.REFLECTION_SUBSTRING_ANALYSIS.value = true
        }

        if (options.DACAPO.value) {
            if (!options.ENABLE_REFLECTION.value) {
                def inputJarName = vars.inputJarFiles[0].toString()
                def deps = inputJarName.replace(".jar", "-deps.jar")
                if (!vars.inputJarFiles.contains(deps))
                    vars.inputJarFiles.add(new File(deps))
                options.TAMIFLEX.value = inputJarName.replace(".jar", "-tamiflex.log")
            }
        }

        if (options.DACAPO_BACH.value) {
            if (!options.ENABLE_REFLECTION.value) {
                def inputJarName = vars.inputJarFiles[0].toString()
                def depsDir = inputJarName.replace(".jar", "-libs")
                new File(depsDir).eachFile { File depsFile ->
                    if (FilenameUtils.getExtension(depsFile.getName()).equals("jar") &&
                        !vars.inputJarFiles.contains(depsFile)) {
                        vars.inputJarFiles.add(depsFile)
                    }
                }
                options.TAMIFLEX.value = inputJarName.replace(".jar", "-tamiflex.log")
            }
        }

        if (options.TAMIFLEX.value) {
            options.ENABLE_REFLECTION.value = false
        }

        if (options.MUST.value) {
            options.SSA.value = true
            options.CFG_ANALYSIS.value = true
            options.MUST_AFTER_MAY.value = true
        }

        checkJRE(vars)

        //Check the value of the JRE_LIB option (it should point to the JREs directory)
        String externals = options.JRE_LIB.value
        FileOps.findDirOrThrow(externals as String, "The JRE_LIB directory is invalid: $externals")

        checkOS(vars)

        if (options.MAIN_CLASS.value) {
            logger.debug "The main class is set to ${options.MAIN_CLASS.value}"
        }
        else {
            JarFile jarFile = new JarFile(vars.inputJarFiles[0])
            //Try to read the main class from the manifest contained in the jar
            String main = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS)
            if (main) {
                logger.debug "The main class is automatically set to ${main}"
                options.MAIN_CLASS.value = main
            }
            else {
                //Check whether the jar contains a class with the same name
                String jarName = FilenameUtils.getBaseName(jarFile.getName())
                if (jarFile.getJarEntry("${jarName}.class")) {
                    logger.debug "The main class is automatically set to ${jarName}"
                    options.MAIN_CLASS.value = jarName
                }
            }
        }

        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            dynFiles.each { String dynFile ->
                FileOps.findFileOrThrow(dynFile, "The DYNAMIC option is invalid: ${dynFile}")
                logger.debug "The DYNAMIC option has been set to ${dynFile}"
            }
        }

        if (options.TAMIFLEX.value) {
            def tamFile = options.TAMIFLEX.value.toString()
            FileOps.findFileOrThrow(tamFile, "The TAMIFLEX option is invalid: ${tamFile}")
        }

        if (!options.ENABLE_REFLECTION.value) {
            if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value ||
                options.REFLECTION_MERGE_MEMBER_CONSTANTS.value ||
                options.REFLECTION_STRING_FLOW_ANALYSIS.value ||
                options.REFLECTION_SUBSTRING_ANALYSIS.value ||
                options.REFLECTION_CONTEXT_SENSITIVITY.value ||
                options.REFLECTION_USE_BASED_ANALYSIS.value ||
                options.REFLECTION_INVENT_UNKNOWN_OBJECTS.value ||
                options.REFLECTION_REFINED_OBJECTS.value) {
                logger.warn "\nWARNING: Probable inconsistent set of Java reflection flags!\n"
            } else if (!options.TAMIFLEX.value) {
                logger.warn "\nWARNING: Handling of Java reflection is disabled!\n"
            } else {
                logger.warn "\nWARNING: Handling of Java reflection via Tamiflex logic!\n"
            }
        }

        logger.debug "---------------"
        for (def option : options) logger.debug option
        logger.debug "---------------"
    }

    /**
     * Checks the JRE version and injects the appropriate JRE option (as expected by the preprocessor logic)
     */
    protected void checkJRE(AnalysisVars vars) {
        String jreValue = vars.options.JRE.value
        if (jreValue == "system")
            jreValue = System.getProperty("java.specification.version")

        logger.debug "Verifying JRE version: $jreValue"

        JRE jreVersion
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
            case "1.8":
                jreVersion = JRE.JRE18
                break
            default:
                throw new RuntimeException("Invalid JRE version: $jreValue")
        }

        //generate the JRE constant for the preprocessor
        AnalysisOption<Boolean> jreOption = new AnalysisOption<Boolean>(
            id:jreVersion.name(),
            value:true,
            forPreprocessor: true
        )
        vars.options[(jreOption.id)] = jreOption
    }

    /**
     * Checks the OS. For now, it is always OS.OS_UNIX (the default).
     */
    protected void checkOS(AnalysisVars vars) {

        OS os = vars.options.OS.value as OS

        //sanity check
        EnumSet<OS> supportedValues = EnumSet.allOf(OS)
        if (! (os in supportedValues)) {
            throw new RuntimeException("Unsupported OS: $os")
        }

        //generate the OS constant for preprocessor
        AnalysisOption<Boolean> osOption = new AnalysisOption<Boolean>(
            id:os.name(),
            value:true,
            forPreprocessor: true
        )
        vars.options[(osOption.id)] = osOption
    }

    /**
     * DACAPO hooks.
    */
    protected void checkDACAPO(AnalysisVars vars) {
        if (vars.options.DACAPO.value) {
            String benchmark = FilenameUtils.getBaseName(vars.inputJarFiles[0].toString())
            logger.info "Running dacapo benchmark: $benchmark"
            vars.options.DACAPO_BENCHMARK.value = benchmark
        }

        if (vars.options.DACAPO_BACH.value) {
            String benchmark = FilenameUtils.getBaseName(vars.inputJarFiles[0].toString())
            logger.info "Running dacapo-bach benchmark: $benchmark"
            vars.options.DACAPO_BENCHMARK.value = benchmark
        }
    }

    /**
     * Determines application classes.
     *
     * If an app regex is not present, it generates one.
     */
    protected void checkAppGlob(AnalysisVars vars) {
        if (!vars.options.APP_REGEX.value) {
            logger.debug "Generating app regex"

            //We process only the first jar for determining the application classes
            /*
            Set excluded = ["*", "**"] as Set
            analysis.jars.drop(1).each { Dependency jar ->
                excluded += Helper.getPackages(jar.input())
            }

            Set<String> packages = Helper.getPackages(analysis.jars[0].input()) - excluded
            */
            Set<String> packages = Helper.getPackages(vars.inputJarFiles[0])
            vars.options.APP_REGEX.value = packages.sort().join(':')
        }
    }

    /**
     * Verifies the correctness of the LogicBlox related options
     */
    protected void checkLogicBlox(AnalysisVars vars) {

        //BLOX_OPTS is set by the main method

        AnalysisOption lbhome = vars.options.LOGICBLOX_HOME

        logger.debug "Verifying LogicBlox home: ${lbhome.value}"

        def lbHomeDir = FileOps.findDirOrThrow(lbhome.value as String, "The ${lbhome.name} value is invalid: ${lbhome.value}")

        def oldldpath = System.getenv("LD_LIBRARY_PATH")
        vars.options.LD_LIBRARY_PATH.value = lbHomeDir.getAbsolutePath() + "/bin" + ":" + oldldpath
        def bloxbatch = lbHomeDir.getAbsolutePath() + "/bin/bloxbatch"
        FileOps.findFileOrThrow(bloxbatch, "The bloxbatch file is invalid: $bloxbatch")
        vars.options.BLOXBATCH.value = bloxbatch
    }

    /**
     * Initializes the external commands environment of the given analysis, by:
     * <ul>
     *     <li>adding the LD_LIBRARY_PATH option to the current environment
     *     <li>modifying PATH to also include the LD_LIBRARY_PATH option
     *     <li>adding the value of the LOGICBLOX_HOME option to the current environment
     * </ul>
     */
    protected Map<String, String> initExternalCommandsEnvironment(AnalysisVars vars) {

        logger.debug "Initializing the environment of the external commands"

        Map<String, String> env = [:]
        env.putAll(System.getenv())

        String path = env.PATH
        AnalysisOption ldLibraryPath = vars.options.LD_LIBRARY_PATH
        if (path) {
            path = "$path${File.pathSeparator}${ldLibraryPath.value}"
        }
        else {
            path = "${ldLibraryPath.value}"
        }
        env.PATH = path
        env.LD_LIBRARY_PATH = ldLibraryPath.value
        env.LOGICBLOX_HOME = vars.options.LOGICBLOX_HOME.value
        env.DOOP_HOME = Doop.doopHome

        return env
    }


    /**
     * Sets all constant options/flags to false. The constant options are determined by their flagType.
     */
    protected void disableAllConstantOptions(Map<String, AnalysisOption> options) {
        logger.debug "Disabling all constant preprocessor flags"
        options.values().each { AnalysisOption option ->
            if (option.forPreprocessor && option.flagType == PreprocessorFlag.CONSTANT_FLAG) {
                option.value = false
            }
        }
    }
}
