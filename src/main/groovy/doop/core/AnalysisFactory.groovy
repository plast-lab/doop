package doop.core

import doop.input.DefaultInputResolutionContext
import doop.input.InputResolutionContext
import doop.preprocess.CppPreprocessor
import doop.preprocess.JcppPreprocessor
import groovy.transform.TypeChecked
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * A Factory for creating Analysis objects.
 *
 * All the methods invoked by newAnalysis (either directly or indirectly) could have been static helpers (.e.g entailed
 * in the doop.core.Helper class) but they are protected instance methods to allow descendants to customize
 * all possible aspects of Analysis creation.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 31/8/2014
 */
@TypeChecked class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())
    static final char[] EXTRA_ID_CHARACTERS = '_-'.toCharArray()

    /**
     * A helper class that acts as an intermediate holder of the analysis variables.
     */
    protected static class AnalysisVars {
        String name
        Map<String, AnalysisOption> options
        Set<String> inputs
        List<File> jars

        @Override
        String toString() {
            return [
                name:name,
                inputs:inputs.toString(),
                jars:jars.toString(),
                options:options.values().toString()
            ].toString()
        }
    }

    /**
     * Creates a new analysis, verifying the correctness of its id, name, options and inputs using
     * the supplied input resolution mechanism.
     * If the supplied id is empty or null, an id will be generated automatically.
     * Otherwise the id will be validated:
     * - if it is valid, it will be used to identify the analysis,
     * - if it is invalid, an exception will be thrown.
     */
    Analysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        //Verify that the name of the analysis is valid
        checkName(name)

        //Resolve the analysis inputs
        List<File> jars = checkInputs(context)

        AnalysisVars vars = new AnalysisVars(
            name   : name,
            options: options,
            inputs : context.inputs(),
            jars   : jars
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

        /*
        Generate id and outDir as the last analysis initialization actions
        */
        String analysisId
        if (id) { //non-empty or null
            //validate and set the user supplied id
            analysisId = validateUserSuppliedId(id)
        }
        else {
            //Generate the id
            analysisId = generateID(vars)
        }

        //Create the outDir if required
        File outDir = createOutputDirectory(vars, analysisId)

        Analysis analysis = new Analysis(
            id           : analysisId,
            outDir       : outDir.toString(),
            name         : name,
            preprocessor : (options.USE_JAVA_CPP.value ? new JcppPreprocessor() : new CppPreprocessor()),
            options      : options,
            ctx          : context,
            jars         : jars,
            commandsEnvironment: commandsEnv
        )

        logger.debug "Created new analysis"
        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputs using
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
     * Generates the analysis ID using all of its components (name, inputs and options).
     */
    protected String generateID(AnalysisVars vars) {
        Collection<String> optionsForId = vars.options.keySet().findAll {
            !Doop.OPTIONS_EXCLUDED_FROM_ID_GENERATION.contains(it)
        }.collect {String option ->
            return vars.options.get(option).toString()
        }
        Collection<String> idComponents = [vars.name] + vars.inputs + optionsForId
        logger.debug("ID components: $idComponents")
        String id = idComponents.join('-')

        return Helper.checksum(id, "SHA-256")
    }

    /**
     * Creates the analysis output dir, if required.
     */
    protected File createOutputDirectory(AnalysisVars vars, String id) {
        String outDir = "${Doop.doopHome}/out/${vars.name}/${id}"
        File f = new File(outDir)
        f.mkdirs()
        Helper.checkDirectoryOrThrowException(outDir, "Could not create analysis directory: ${outDir}")
        return f
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

        if (options.DISABLE_REFLECTION.value) {
            logger.debug "The DISABLE_REFLECTION option has been enabled"
            //NOTE:the flag noreflection is set but we don't need it as a separate option/flag 
        }

        if (options.CONTEXT_SENSITIVE_REFLECTION.value) {
            logger.debug "The CONTEXT_SENSITIVE_REFLECTION option has been enabled"
        }

        if (options.CLIENT_EXCEPTION_FLOW.value) {
            logger.debug "The CLIENT_EXCEPTION_FLOW option has been enabled"
        }

        if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
            logger.debug "The DISTINGUISH_ALL_STRING_CONSTANTS option has been enabled"
        }

        if (options.DISTINGUISH_REFLECTION_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_REFLECTION_STRING_CONSTANTS.value = true
            logger.debug "The DISTINGUISH_REFLECTION_STRING_CONSTANTS option has been enabled"
        }

        if (options.DISTINGUISH_NO_STRING_CONSTANTS.value) {
            disableAllConstantOptions(options)
            options.DISTINGUISH_NO_STRING_CONSTANTS.value = true
            logger.debug "The DISTINGUISH_NO_STRING_CONSTANTS option has been enabled"
        }

        if (!options.REFLECTION_STRING_FLOW_ANALYSIS.value) {
            logger.debug "The REFLECTION_STRING_FLOW_ANALYSIS option has been disabled"
        }

        if (!options.ANALYZE_REFLECTION_SUBSTRINGS.value) {
            logger.debug "The ANALYZE_REFLECTION_SUBSTRINGS option has been disabled"
        }

        if (!options.MERGE_FIELD_AND_METHOD_SUBSTRINGS.value) { 
            logger.debug "The MERGE_FIELD_AND_METHOD_SUBSTRINGS option has been disabled"
        }

        if (options.USE_BASED_REFLECTION_ANALYSIS.value) { 
            logger.debug "The USE_BASED_REFLECTION_ANALYSIS option has been enabled"
        }

        if (options.INVENT_UNKNOWN_REFLECTIVE_OBJECTS.value) {
            logger.debug "The INVENT_UNKNOWN_REFLECTIVE_OBJECTS option has been enabled"
        }

        if (options.REFINED_REFLECTION_OBJECTS.value) {
            logger.debug "The REFINED_REFLECTION_OBJECTS option has been enabled"
        }

        if (!options.INCLUDE_IMPLICITLY_REACHABLE_CODE.value) {
            logger.debug("The INCLUDE_IMPLICITLY_REACHABLE_CODE option has been disabled")
        }

        if (!options.MERGE_STRING_BUFFERS.value) {
            logger.debug "The MERGE_STRING_BUFFERS option has been disabled"
        }

        if (options.NO_CONTEXT_REPEAT.value) {
            logger.debug "The NO_CONTEXT_REPEAT option has been enabled"
        }

        if (options.TRANSFORM_INPUT.value) {
            options.SET_BASED.value = true
            logger.debug "The TRANSFORM_INPUT option has been enabled"
        }

        if (options.SSA.value) {
            logger.debug "The SSA option has been enabled"
        }

        if (options.CACHE.value) {
            logger.debug "The CACHE option has been enabled"
        }

        if (options.STATS.value) {
            logger.debug "The STATS option has been enabled"
        }

        if (options.SANITY.value) {
            logger.debug "The SANITY option has been enabled"
        }

        if (options.MEMLOG.value) {
            logger.debug "The MEMLOG option has been enabled"
        }

        if (options.INTERACTIVE.value) {
            logger.debug "The INTERACTIVE option has been enabled"
        }
        
        if (options.AVERROES.value) {
            logger.debug "The AVERROES option has been enabled"
        }
        
        if (options.RUN_JPHANTOM.value) {
            logger.debug "The RUN_JPHANTOM option has been enabled"
        }
        
        if (options.DACAPO.value) {
            logger.debug "The DACAPO option has been enabled"
        }
        
        if (options.DACAPO_BACH.value) {
            logger.debug "The DACAPO_BACH option has been enabled"
        }

        if (options.TAMIFLEX.value) {
            options.DISABLE_REFLECTION.value = true
            options.REFLECTION_STRING_FLOW_ANALYSIS.value = false
            options.ANALYZE_REFLECTION_SUBSTRINGS.value = false
            logger.debug "The TAMIFLEX option has been enabled"
        }

        // Checks for must analyses

        if(isMustPointTo(vars.name) && !options.MAY_PRE_ANALYSIS.value)
            throw new UnsupportedOperationException("A ''plain'' must-point-to analysis is not supported yet")

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
        
        checkOS(vars)
        
        if (options.MAIN_CLASS.value) {
            logger.debug "The main class is set to ${options.MAIN_CLASS.value}"
        }
        else {
            JarFile jarFile = new JarFile(vars.jars[0])
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
        
        if (options.INCREMENTAL.value) {
            logger.debug "The INCREMENTAL option has been enabled"
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
        
        if (options.CLIENT_CODE.value) {
            String clFile = options.CLIENT_CODE.value.toString()
            Helper.checkFileOrThrowException(clFile, "The CLIENT_CODE option is invalid: ${clFile}")
            options.CLIENT_EXTENSIONS.value = true
            logger.debug "The CLIENT_CODE option has been set to ${clFile}"
        }
    }
    
    /**
     * Checks the JRE version and injects the appropriate JRE option (as expected by the preprocessor logic)
     */
    protected void checkJRE(AnalysisVars vars) {

        JRE jreVersion
        String jreValue = vars.options.JRE.value

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
            String benchmark = FilenameUtils.getBaseName(vars.jars[0].toString())
            logger.info "Running dacapo benchmark: $benchmark"
            //We don't hard-code the dependencies, we just set the appropriate flags
            vars.options.DACAPO_BENCHMARK.value = benchmark
            return
        }
        
        if (vars.options.DACAPO_BACH.value) {
            String benchmark = FilenameUtils.getBaseName(vars.jars[0].toString())
            logger.info "Running dacapo-2009 benchmark: $benchmark"
            //We don't hard-code the dependencies, we just set the appropriate flags
            vars.options.DACAPO_2009.value = true
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
            Set<String> packages = Helper.getPackages(vars.jars[0])
            vars.options.APP_REGEX.value = packages.sort().join(':')
        }
    }
    
    /**
     * Verifies the correctness of the LogicBlox related options
     */
    protected void checkLogicBlox(AnalysisVars vars) {

        //TODO: Process bloxopts
    
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
