package doop.core

import doop.input.DefaultInputResolutionContext
import doop.input.InputResolutionContext
import groovy.transform.TypeChecked
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * A Factory for creating Analysis objects.
 *
 * All the methods invoked by newAnalysis (either directly or indirectly) could have been static helpers (e.g. entailed
 * in the doop.core.Helper class) but they are protected instance methods to allow descendants to customize
 * all possible aspects of Analysis creation.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 31/8/2014
 */
@TypeChecked class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())
    static final char[] EXTRA_ID_CHARACTERS = '_-'.toCharArray()
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
        String analysisPath = "${Doop.doopLogic}/analyses/${name}/analysis.logic"
        Helper.checkFileOrThrowException(analysisPath, "Unsupported analysis: $name")
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
        String outDir = "${Doop.doopOut}/${vars.name}/${id}"
        File f = new File(outDir)
        f.mkdirs()
        Helper.checkDirectoryOrThrowException(outDir, "Could not create analysis directory: ${outDir}")
        return f
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

        return Helper.checksum(id, HASH_ALGO)
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

        Collection<String> checksums = new File("${Doop.doopLogic}/facts").listFiles().collect {
            File file -> Helper.checksum(file, HASH_ALGO)
        }

        checksums += vars.inputJarFiles.collect {
            File file -> Helper.checksum(file, HASH_ALGO)
        }

        checksums += vars.jreJars.collect {
            String file -> Helper.checksum(new File(file), HASH_ALGO)
        }

        if(vars.options.TAMIFLEX.value) {
            checksums += [Helper.checksum(new File(vars.options.TAMIFLEX.value.toString()), HASH_ALGO)]
        }

        File checksumsFile = Helper.checkFileOrThrowException("${Doop.doopHome}/checksums.properties", "Invalid checksums")
        Properties p = Helper.loadProperties(checksumsFile)
        checksums += [p.getProperty(Doop.SOOT_CHECKSUM_KEY), p.getProperty(Doop.JPHANTOM_CHECKSUM_KEY)]

        idComponents = checksums + idComponents

        logger.debug("Cache ID components: $idComponents")
        String id = idComponents.join('-')

        return Helper.checksum(id, HASH_ALGO)
    }

    /**
     * Generates a list of the jre link arguments for soot
     */
    protected List<String> jreLinkArgs(Map<String, AnalysisOption> options) {

        String jre = options.JRE.value
        String path = "${options.EXTERNALS.value}/jre${jre}/lib"

        switch(jre) {
            case "1.3":
                return Helper.checkFiles(["${path}/rt.jar".toString()])
            case "1.4":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.5":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.6":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.7":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString(),
                                          "${path}/rhino.jar".toString()])
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

        if (options.PADDLE_COMPAT.value) {
            disableAllExceptionOptions(options)
            logger.debug "The PADDLE_COMPAT option has been enabled"
        }

        if (options.DISABLE_PRECISE_EXCEPTIONS.value) {           
            disableAllExceptionOptions(options)
        }

        if (options.EXCEPTIONS_IMPRECISE.value) {
            disableAllExceptionOptions(options)
            options.EXCEPTIONS_IMPRECISE.value = true
            logger.debug "The EXCEPTIONS_IMPRECISE option has been enabled"
        }

        if (options.DISABLE_MERGE_EXCEPTIONS.value) {
            disableAllExceptionOptions(options)
            options.EXCEPTIONS_PRECISE.value = true
            options.SEPARATE_EXCEPTION_OBJECTS.value = true
            logger.debug "The DISABLE_MERGE_EXCEPTIONS option has been enabled"
        }

        if (options.EXCEPTIONS_EXPERIMENTAL.value) {
            disableAllExceptionOptions(options)
            options.EXCEPTIONS_EXPERIMENTAL.value = true
            logger.debug "The EXCEPTIONS_EXPERIMENTAL option has been enabled"
        }

        if (options.EXCEPTIONS_FILTER.value) {
            logger.debug "The EXCEPTIONS_FILTER option has been enabled"
        }

        if (options.EXCEPTIONS_ORDER.value) {
            logger.debug "The EXCEPTIONS_ORDER option has been enabled"
        }

        if (options.EXCEPTIONS_RANGE.value) {
            logger.debug "The EXCEPTIONS_RANGE option has been enabled"
        }

        if (options.EXCEPTIONS_CS.value) {
            logger.debug "The EXCEPTIONS_CS option has been enabled"
        }

        if (options.FU_EXCEPTION_FLOW.value) {
            logger.debug "The FU_EXCEPTION_FLOW option has been enabled"
        }

        if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
            logger.debug "The DISTINGUISH_ALL_STRING_CONSTANTS option has been enabled"
        }

        if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.PADDLE_COMPAT.value = false
            logger.debug "The DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS option has been enabled"
        } else {
            // Merging of method and field names happens only if we distinguish
            // reflection strings in the first place.
            options.REFLECTION_MERGE_MEMBER_CONSTANTS.value = false
            logger.debug "The DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS option has been disabled"
        }

        if (options.DISTINGUISH_NO_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_NO_STRING_CONSTANTS.value = true
            options.PADDLE_COMPAT.value = false
            logger.debug "The DISTINGUISH_NO_STRING_CONSTANTS option has been enabled"
        }

        if (options.REFLECTION_STRING_FLOW_ANALYSIS.value) {
            logger.debug "The REFLECTION_STRING_FLOW_ANALYSIS option has been enabled"
        } else {
            // It makes no sense to analyze partial strings that may match fields
            // when we don't track the flow of these strings through StringBuilders.
            options.REFLECTION_SUBSTRING_ANALYSIS.value = false
            logger.debug "The REFLECTION_STRING_FLOW_ANALYSIS option has been disabled"
        }

        if (options.REFLECTION_SUBSTRING_ANALYSIS.value) {
            logger.debug "The REFLECTION_SUBSTRING_ANALYSIS option has been enabled"
        }

        if (options.REFLECTION_MERGE_MEMBER_CONSTANTS.value) { 
            logger.debug "The REFLECTION_MERGE_MEMBER_CONSTANTS option has been enabled"
        }

        if (options.ENABLE_REFLECTION.value) {
            logger.debug "The ENABLE_REFLECTION option has been enabled"
        }

        if (options.ENABLE_REFLECTION_CLASSIC.value) {
            options.DISTINGUISH_NO_STRING_CONSTANTS.value = false
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.ENABLE_REFLECTION.value = true
            options.REFLECTION_MERGE_MEMBER_CONSTANTS.value = true
            options.REFLECTION_STRING_FLOW_ANALYSIS.value = true
            options.REFLECTION_SUBSTRING_ANALYSIS.value = true
            logger.debug "The ENABLE_REFLECTION_CLASSIC option has been enabled"
        }

        if (options.REFLECTION_CONTEXT_SENSITIVITY.value) {
            logger.debug "The REFLECTION_CONTEXT_SENSITIVITY option has been enabled"
        }

        if (options.REFLECTION_USE_BASED_ANALYSIS.value) { 
            logger.debug "The REFLECTION_USE_BASED_ANALYSIS option has been enabled"
        }

        if (options.REFLECTION_INVENT_UNKNOWN_OBJECTS.value) {
            logger.debug "The REFLECTION_INVENT_UNKNOWN_OBJECTS option has been enabled"
        }

        if (options.REFLECTION_REFINED_OBJECTS.value) {
            logger.debug "The REFLECTION_REFINED_OBJECTS option has been enabled"
        }

        if (!options.INCLUDE_IMPLICITLY_REACHABLE_CODE.value) {
            logger.debug("The INCLUDE_IMPLICITLY_REACHABLE_CODE option has been disabled")
        }

        if (!options.MERGE_STRING_BUFFERS.value) {
            logger.debug "The MERGE_STRING_BUFFERS option has been disabled"
        }

        if (options.TRANSFORM_INPUT.value) {
            logger.debug "The TRANSFORM_INPUT option has been enabled"
        }

        if (options.SSA.value) {
            logger.debug "The SSA option has been enabled"
        }

        if (options.CACHE.value) {
            logger.debug "The CACHE option has been enabled"
        }

        if (options.FULL_STATS.value) {
            logger.debug "The FULL_STATS option has been enabled"
        }

        if (options.NO_STATS.value) {
            logger.debug "The NO_STATS option has been enabled"
        }

        if (options.SANITY.value) {
            logger.debug "The SANITY option has been enabled"
        }

        if (options.RUN_AVERROES.value) {
            logger.debug "The RUN_AVERROES option has been enabled"
        }
        
        if (options.RUN_JPHANTOM.value) {
            logger.debug "The RUN_JPHANTOM option has been enabled"
        }
        
        if (options.DACAPO.value) {
            logger.debug "The DACAPO option has been enabled"
            if (!options.ENABLE_REFLECTION.value) {
                def inputJarName = vars.inputJarFiles[0].toString()
                def deps = inputJarName.replace(".jar", "-deps.jar")
                if (!vars.inputJarFiles.contains(deps))
                    vars.inputJarFiles.add(new File(deps))
                options.TAMIFLEX.value = inputJarName.replace(".jar", "-tamiflex.log")
                logger.debug "The TAMIFLEX option has been enabled (due to DACAPO)"
            }
        }
        
        if (options.DACAPO_BACH.value) {
            logger.debug "The DACAPO_BACH option has been enabled"
            if (!options.ENABLE_REFLECTION.value) {
                def inputJarName = vars.inputJarFiles[0].toString()
                def deps = inputJarName.replace(".jar", "-libs")
                if (!vars.inputJarFiles.contains(deps))
                    vars.inputJarFiles.add(new File(deps))
                options.TAMIFLEX.value = inputJarName.replace(".jar", "-tamiflex.log")
                logger.debug "The TAMIFLEX option has been enabled (due to DACAPO_BACH)"
            }
        }

        if (options.TAMIFLEX.value) {
            options.ENABLE_REFLECTION.value = false
            logger.debug "The TAMIFLEX option has been enabled"
        }

        // Check for SOOT option (cannot be null)
        if (!options.SOOT.value) {
            throw new RuntimeException("The SOOT option is null")
        }

        // Checks for must analyses

        if(isMustPointTo(vars.name) && !options.MAY_PRE_ANALYSIS.value)
            throw new UnsupportedOperationException("For a must-point-to analysis, you need to specify a may- pre-analysis")

        if(options.MAY_PRE_ANALYSIS.value) {
            if(!isMustPointTo(vars.name))
                throw new RuntimeException("Option: " + options.MAY_PRE_ANALYSIS.name + " is used only for must-analyses")

            options.MUST_AFTER_MAY.value = true
            logger.debug "The MUST_AFTER_MAY flag has been enabled"
        }

        if(isMustPointTo(vars.name) && !options.SSA.value) {
            options.SSA.value = true
            logger.debug "The SSA flag has been enabled (must-point-to)"
        }

        checkJRE(vars)

        //Check the value of the EXTERNALS option (it should point to the JREs directory)
        String externals = options.EXTERNALS.value
        Helper.checkDirectoryOrThrowException(externals as String, "The EXTERNALS directory is invalid: $externals")
        
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
                Helper.checkFileOrThrowException(dynFile, "The DYNAMIC option is invalid: ${dynFile}")
                logger.debug "The DYNAMIC option has been set to ${dynFile}"
            }
        }
        
        if (options.TAMIFLEX.value) {
            String tamFile = options.TAMIFLEX.value.toString()
            Helper.checkFileOrThrowException(tamFile, "The TAMIFLEX option is invalid: ${tamFile}")
            logger.debug "The TAMIFLEX option has been set to ${tamFile}"
        }
        
        if (options.AUXILIARY_HEAP.value) {
            logger.debug "The AUXILIARY_HEAP option has been enabled"
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
        String lbHomePath = lbhome.value

        logger.debug "Verifying LogicBlox home: $lbHomePath"

        File lbHomeJavaFile = Helper.checkDirectoryOrThrowException(lbHomePath as String, "The ${lbhome.name} value is invalid: ${lbhome.value}")

        vars.options.LD_LIBRARY_PATH.value = lbHomeJavaFile.getAbsolutePath() + "/bin"
        String bloxbatch = lbHomeJavaFile.getAbsolutePath() + "/bin/bloxbatch"
        Helper.checkFileOrThrowException(bloxbatch, "The bloxbatch file is invalid: $bloxbatch")
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
     * Sets all exception options/flags to false. The exception options are determined by their flagType.
     */
    protected void disableAllExceptionOptions(Map<String, AnalysisOption> options) {
        logger.debug "Disabling all exception preprocessor flags"
        options.values().each { AnalysisOption option ->
            if (option.forPreprocessor && option.flagType == PreprocessorFlag.EXCEPTION_FLAG) {
                option.value = false
            }
        }
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


    protected boolean isMustPointTo(String name) {
        return Helper.isMustPointTo(name)
    }
}
