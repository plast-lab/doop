package doop.core

import doop.input.DefaultInputResolutionContext
import doop.input.InputResolutionContext
import doop.preprocess.CppPreprocessor
import doop.preprocess.JcppPreprocessor
import doop.resolve.Dependency
import doop.resolve.ExistingFileDependency
import doop.resolve.StringDependency
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.util.jar.Attributes
import java.util.jar.JarFile
/**
 * A Factory for creating Analysis objects.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 31/8/2014
 */
class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputs using
     * the supplied input resolution mechanism.
     */
    Analysis newAnalysis(String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        //Verify that the name of the analysis is valid
        checkName(name)

        //Generate the id
        String id = generateID(name, context.inputs(), options)

        //Create the outDir if required
        File outDir = createOuputDirectory(name, id)

        context.setDirectory(outDir)

        Analysis analysis = new Analysis(
                name         : name,
                id           : id,
                outDir       : outDir.toString(),
                preprocessor : (options.USE_JAVA_CPP.value ? new JcppPreprocessor() : new CppPreprocessor()),
                options      : options,
                ctx          : context
        )

        //Resolve the analysis inputs
        checkInputs(analysis, context)

        //process the options
        processOptions(analysis)

        //verify lb options
        checkLogicBlox(analysis)

        //init the environment used for executing commands
        initExternalCommandsEnvironment(analysis)

        //TODO: The COLOR option is not supported

        //TODO: Create empty jar. Is it needed?

        //TODO: Check if input is given (incremental). Is it needed?
        checkDACAPO(analysis)

        checkAppGlob(analysis)

        //We don't need to renew the averroes properties file here (we do it in Analysis.runAverroes())

        //TODO: Add client code extensions into the main logic (/bin/weave-client-logic)

        //TODO: Check that only one instance of bloxbatch is running if SOLO option is enabled

        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputs using
     * the default input resolution mechanism.
     */
    Analysis newAnalysis(String name, Map<String, AnalysisOption> options, List<String> jars) {
        DefaultInputResolutionContext context = new DefaultInputResolutionContext()
        context.add(jars)
        return newAnalysis(name, options, context)
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
     * Generates the analysis ID, using its name, main class and inputs.
     *
     * NOTE: With the input resolution feature, the APP_REGEX is not being used (because it depends on the resolution
     * of the inputs, which depend on the ID).
     * @param analysis
     */
    protected String generateID(String name, Collection<String> inputs, Map<String, AnalysisOption> options) {
        logger.debug "Generating analysis ID"

        def idComponents = [name, options.MAIN_CLASS.value] + inputs
        String id = idComponents.collect { it.toString() }.join('-')

        //Generate a sha256 cheksum of the id components
        return Helper.checksum(id, "SHA-256")
    }

    /**
     * Creates the analysis output dir, if required.
     */
    protected File createOuputDirectory(String name, String id) {
        String outDir = "${Doop.doopHome}/out/$name/${id}"
        File f = new File(outDir)
        f.mkdirs()
        Helper.checkDirectoryOrThrowException(outDir, "Could not create analysis directory: ${outDir}")
        return f
    }

    /**
     * Given the list of analysis inputs, as Strings, the method validates that the inputs exist,
     * using the input resolution mechanism. It finally adds the inputs as a Set<File> in the analysis.
     */
    protected void checkInputs(Analysis analysis, InputResolutionContext context) {
        Collection<String> inputs = context.inputs()
        logger.debug "Verifying analysis inputs: $inputs"
        if (!inputs) throw new RuntimeException("No inputs provided for the analysis")
        context.resolve()
        analysis.jars = context.getAll()
    }

    @Deprecated
    /**
     * Given the list of jars, as Strings or Dependency objects, the method validates that the jars exist,
     * using the jar resolution mechanism. It finally adds the jars as a List<Dependency> in the analysis.
     * The method detects if a String refers to a directory path and, if so, it adds all the *.jar files
     * included therein.
     */
    protected void checkJars(Analysis analysis, List jars) {
        logger.debug "Verifying analysis input jars: $jars"
        if (!jars) throw new RuntimeException("No jars provided for the analysis")
        analysis.jars = jars.collect { Object jar ->
            if (jar) {
                if (jar instanceof String) {
                    try {
                        File f = Helper.checkDirectoryOrThrowException(jar, null)
                        logger.debug("Resolving jars in directory $f")

                        def filter = Helper.extensionFilter("jar")

                        def filesInDir = []
                        f.listFiles(filter).each { File file ->
                            filesInDir.push new ExistingFileDependency(file)
                        }

                        def files = filesInDir.sort{ it.toString() }
                        logger.debug("Resolved ${files.size()} jars in directory $f: $files")
                        return files
                    }
                    catch(e) {
                        StringDependency jarDep = new StringDependency(jar, analysis)
                        logger.debug "Resolving $jar"
                        jarDep.resolve()
                        return jarDep
                    }
                }
                else if (jar instanceof Dependency) {
                    logger.debug "Resolving $jar"
                    jar.resolve()
                    return jar
                }
                else {
                    throw new RuntimeException("Cannot resolve jar dependency ${jar.getClass()}: $jar")
                }
            }
            else {
                throw new RuntimeException("Null value in analysis jars")
            }
        }.flatten()
    }
	
	/**
     * Processes the options of the analysis.
     */
    protected void processOptions(Analysis analysis) {
	
		logger.debug "Processing analysis options"
		
        Map<String, AnalysisOption> options = analysis.options
		
		/*
		 * We mimic the checks of the run script for verifiability of this implementation, 
		 * even though the majority of checks are not required.
		 */

        if (options.PADDLE_COMPAT.value) {
			analysis.disableAllExceptionOptions()
			logger.debug "The PADDLE_COMPAT option has been enabled"
        }

        if (options.DISABLE_PRECISE_EXCEPTIONS.value) {           
			analysis.disableAllExceptionOptions()
        }

        if (options.EXCEPTIONS_IMPRECISE.value) {
			analysis.disableAllExceptionOptions()
			options.EXCEPTIONS_IMPRECISE.value = true
			logger.debug "The EXCEPTIONS_IMPRECISE option has been enabled"
			
        }

        if (options.DISABLE_MERGE_EXCEPTIONS.value) {
			analysis.disableAllExceptionOptions()
            options.EXCEPTIONS_PRECISE.value = true
            options.SEPARATE_EXCEPTION_OBJECTS.value = true
			logger.debug "The DISABLE_MERGE_EXCEPTIONS option has been enabled"
        }

        if (options.EXCEPTIONS_EXPERIMENTAL.value) {
			analysis.disableAllExceptionOptions()
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
			analysis.disableAllConstantOptions()
			options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
			logger.debug "The DISTINGUISH_ALL_STRING_CONSTANTS option has been enabled"
        }

        if (options.DISTINGUISH_REFLECTION_STRING_CONSTANTS.value) {
			analysis.disableAllConstantOptions()
			options.DISTINGUISH_REFLECTION_STRING_CONSTANTS.value = true
			logger.debug "The DISTINGUISH_REFLECTION_STRING_CONSTANTS option has been enabled"
        }

        if (options.DISTINGUISH_NO_STRING_CONSTANTS.value) {
			analysis.disableAllConstantOptions()
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

        if (options.SOLO.value) {
            logger.debug "The SOLO option has been enabled"
        }
		
		if (options.COLOR.value) {
			logger.debug "The COLOR option has been enabled"
		}
		
		if (options.INTERACTIVE.value) {
			logger.debug "The INTERACTIVE option has been enabled"
		}
		
		if (options.AVERROES.value) {
			logger.debug "The AVERROES option has been enabled"
		}
		
		if (options.ALLOW_PHANTOM.value) {
			logger.debug "The ALLOW_PHANTOM option has been enabled"
		}
		
		if (options.DACAPO.value) {
			logger.debug "The DACAPO option has been enabled"
		}
		
		if (options.DACAPO_BACH.value) {
			logger.debug "The DACAPO_BACH option has been enabled"
		}

        // Checks for must analyses

        if(analysis.isMustPointTo() && !options.MAY_PRE_ANALYSIS.value)
            throw new UnsupportedOperationException("A ''plain'' must-point-to analysis is not supported yet")

        if(options.MAY_PRE_ANALYSIS.value) {
            if(!analysis.isMustPointTo())
                throw new RuntimeException("Option: " + option.MAY_PRE_ANALYSIS.name + " is used only for must-analyses")

            options.MUST_AFTER_MAY.value = true
            logger.debug "The MUST_AFTER_MAY flag has been enabled"
        }

        if(analysis.isMustPointTo() && !options.SSA.value) {
            options.SSA.value = true
            logger.debug "The SSA flag has been enabled by default"
        }

		checkJRE(analysis)
		
		checkOS(analysis)
		
		if (options.MAIN_CLASS.value) {
			logger.debug "The main class is set to ${options.MAIN_CLASS.value}"
		}
        else {
            JarFile jarFile = new JarFile(analysis.jars[0])
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
			List<String> dynFiles = options.DYNAMIC.value
            dynFiles.each { String dynFile ->
                Helper.checkFileOrThrowException(dynFile, "The DYNAMIC option is invalid: ${dynFile}")
                logger.debug "The DYNAMIC option has been set to ${dynFile}"
            }
		}
		
		if (options.TAMIFLEX.value) {
			String tamFile = options.TAMIFLEX.value
			Helper.checkFileOrThrowException(tamFile, "The TAMIFLEX option is invalid: ${tamFile}")
			logger.debug "The TAMIFLEX option has been set to ${tamFile}"
		}
		
		if (options.CLIENT_CODE.value) {
			String clFile = options.CLIENT_CODE.value
			Helper.checkFileOrThrowException(clFile, "The CLIENT_CODE option is invalid: ${clFile}")
			options.CLIENT_EXTENSIONS.value = true
			logger.debug "The CLIENT_CODE option has been set to ${clFile}"
		}
    }
	
	/**
     * Checks the JRE version and injects the appropriate JRE option (as expected by the preprocessor logic)
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

        //generate the JRE constant for the preprocessor
        AnalysisOption<Boolean> jreOption = new AnalysisOption<Boolean>(
			id:jreVersion.name(),
			value:true,
			forPreprocessor: true
        )
        analysis.options[(jreOption.id)] = jreOption
	}
	
	/**
	 * Checks the OS. For now, it is always OS.OS_UNIX (the default).
	 */
	protected void checkOS(Analysis analysis) {

        OS os = analysis.options.OS.value as OS

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
        analysis.options[(osOption.id)] = osOption
    }

    /**
     * DACAPO hooks.
    */
    protected void checkDACAPO(Analysis analysis) {
        if (analysis.options.DACAPO.value) {
            String benchmark = FilenameUtils.getBaseName(analysis.jars[0].toString())
            logger.info "Running dacapo benchmark: $benchmark"
            //We don't hard-code the dependencies, we just set the appropriate flags
            analysis.options.DACAPO_BENCHMARK.value = benchmark
            return
        }
        
        if (analysis.options.DACAPO_BACH.value) {
            String benchmark = FilenameUtils.getBaseName(analysis.jars[0].toString())
            logger.info "Running dacapo-2009 benchmark: $benchmark"
            //We don't hard-code the dependencies, we just set the appropriate flags
            analysis.options.DACAPO_2009.value = true
            analysis.options.DACAPO_BENCHMARK.value = benchmark
        }
    }
	
	/**
     * Determines application classes.
     *
     * If an app regex is not present, it generates one.
     */
    protected void checkAppGlob(Analysis analysis) {
        if (!analysis.options.APP_REGEX.value) {
            logger.debug "Generating app regex"
			
            //We process only the first jar for determining the application classes
            /*
			Set excluded = ["*", "**"] as Set
			analysis.jars.drop(1).each { Dependency jar ->
				excluded += Helper.getPackages(jar.resolve())
			}

			Set<String> packages = Helper.getPackages(analysis.jars[0].resolve()) - excluded
            */
            Set<String> packages = Helper.getPackages(analysis.jars[0])
			analysis.options.APP_REGEX.value = packages.sort().join(':')
        }
    }
	
	/**
     * Verifies the correctness of the LogicBlox related options
     */
    protected void checkLogicBlox(Analysis analysis) {

		//TODO: Process bloxopts
	
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
     * Initializes the external commands environment of the given analysis, by:
     * <ul>
     *     <li>adding the LD_LIBRARY_PATH option to the current environment
     *     <li>modifying PATH to also include the LD_LIBRARY_PATH option
     *     <li>adding the value of the LOGICBLOX_HOME option to the current environment
     * </ul>
     */
    protected Map<String, String> initExternalCommandsEnvironment(Analysis analysis) {

        logger.debug "Initializing the environment of the external commands"
        
		Map<String, String> env = [:]
        env.putAll(System.getenv())
		
        String path = env.PATH
		AnalysisOption ldLibraryPath = analysis.options.LD_LIBRARY_PATH
        if (path) {
            path = "$path${File.pathSeparator}${ldLibraryPath.value}"
        }
        else {
            path = "${ldLibraryPath.value}"
        }
        env.PATH = path
        env.LD_LIBRARY_PATH = ldLibraryPath.value
        env.LOGICBLOX_HOME = analysis.options.LOGICBLOX_HOME.value
        env.DOOP_HOME = Doop.doopHome

        analysis.commandsEnvironment = env
    }
}
