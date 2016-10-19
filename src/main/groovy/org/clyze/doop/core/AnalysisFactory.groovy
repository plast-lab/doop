package org.clyze.doop.core

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
 * All the methods invoked by newAnalysis (either directly or indirectly) could
 * have been static helpers (e.g. entailed in the Helper class) but they are
 * protected instance methods to allow descendants to customize all possible
 * aspects of Analysis creation.
 */
class AnalysisFactory {

    Log logger = LogFactory.getLog(getClass())
    static final char[] EXTRA_ID_CHARACTERS = '_-.'.toCharArray()
    static final String HASH_ALGO = "SHA-256"

    /**
     * A helper class that acts as an intermediate holder of the analysis variables.
     */
    protected static class AnalysisVars {
        String name
        Map<String, AnalysisOption> options
        List<String> inputFilePaths
        List<String> platformFilePaths
        List<File>   inputFiles
        List<File>   platformFiles

        @Override
        String toString() {
            return [
                name:              name,
                options:           options.values().toString(),
                inputFilePaths:    inputFilePaths.toString(),
                platformFilePaths: platformFilePaths.toString(),
                inputFiles:        inputFiles.toString(),
                platformFiles:     platformFiles.toString()
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

        def vars = processOptions(name, options, context)

        checkAnalysis(name)

        checkLogicBlox(vars)

        checkAppGlob(vars)

        //init the environment used for executing commands
        Map<String, String> commandsEnv = initExternalCommandsEnvironment(vars)

        // if not empty or null
        def analysisId = id ? validateUserSuppliedId(id) : generateId(vars)

        def cacheId = generateCacheID(vars)

        def outDir = createOutputDirectory(vars, analysisId)

        def cacheDir = new File("${Doop.doopCache}/$cacheId")

        Analysis analysis = new Analysis(
            analysisId,
            outDir.toString(),
            cacheDir.toString(),
            name,
            options,
            context,
            vars.inputFiles,
            vars.platformFiles,
            commandsEnv
        )
        logger.debug "Created new analysis"
        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputs using
     * the default input resolution mechanism.
     */
    Analysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, List<String> inputFilePaths) {
        DefaultInputResolutionContext context = new DefaultInputResolutionContext()
        context.add(inputFilePaths)
        return newAnalysis(id, name, options, context)
    }

    protected void checkAnalysis(String name) {
        logger.debug "Verifying analysis name: $name"
        def analysisPath = "${Doop.analysesPath}/${name}/analysis.logic"
        FileOps.findFileOrThrow(analysisPath, "Unsupported analysis: $name")
    }

    protected String validateUserSuppliedId(String id) {
        def trimmed = id.trim()
        def isValid = trimmed.toCharArray().every {
            c -> Character.isLetter(c) || Character.isDigit(c) || c in EXTRA_ID_CHARACTERS
        }

        if (!isValid) {
            throw new RuntimeException("Invalid analysis id: $id. The id should contain only letters, digits, " +
                    "${EXTRA_ID_CHARACTERS.collect{"'$it'"}.join(', ')}.")
        }
        return trimmed
    }

    protected File createOutputDirectory(AnalysisVars vars, String id) {
        def outDir = new File("${Doop.doopOut}/${vars.name}/${id}")
        outDir.mkdirs()
        FileOps.findDirOrThrow(outDir, "Could not create analysis directory: ${outDir}")
        return outDir
    }

    protected String generateId(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.keySet().findAll {
            !Doop.OPTIONS_EXCLUDED_FROM_ID_GENERATION.contains(it)
        }.collect {
            String option -> return vars.options.get(option).toString()
        }
        idComponents = [vars.name] + vars.inputFilePaths + idComponents
        logger.debug("ID components: $idComponents")
        def id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    protected String generateCacheID(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.values()
            .findAll { it.forCacheID }
            .collect { option -> option.toString() }

        Collection<String> checksums = new File(Doop.factsPath).listFiles().collect {
            File file -> CheckSum.checksum(file, HASH_ALGO)
        }
        checksums += vars.inputFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }
        checksums += vars.platformFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }

        if (vars.options.TAMIFLEX.value)
            checksums += [CheckSum.checksum(new File(vars.options.TAMIFLEX.value.toString()), HASH_ALGO)]

        def checksumsFile = FileOps.findFileOrThrow("${Doop.doopHome}/checksums.properties", "Invalid checksums")
        def props = FileOps.loadProperties(checksumsFile)
        checksums += [props.getProperty(Doop.SOOT_CHECKSUM_KEY)]

        idComponents = checksums + idComponents

        logger.debug("Cache ID components: $idComponents")
        def id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    /**
     * Generates a list of the platform library arguments for soot
     */
    protected List<String> platform(Map<String, AnalysisOption> options) {
        def platformInfo = options.PLATFORM.value.toString().tokenize("_")
        def (platform, version) = [platformInfo[0], platformInfo[1].toInteger()]

        def files = []
        switch(platform) {
            case "java":
                String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"
                switch(version) {
                    case 3:
                        files = ["${path}/rt.jar"]
                        break
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                        files = ["${path}/rt.jar",
                                 "${path}/jce.jar",
                                 "${path}/jsse.jar"]
                        break
                    default:
                        throw new RuntimeException("Invalid JRE version: $version")
                }
                // generate the JRE constant for the preprocessor
                AnalysisOption<Boolean> jreOption = new AnalysisOption<Boolean>(
                    id:"JRE1"+version,
                    value:true,
                    forPreprocessor: true
                )
                options[(jreOption.id)] = jreOption
                break
            case "android":
                String path = "${options.PLATFORMS_LIB.value}/Android/Sdk/platforms/android-${version}"
                // Set this flag to true to have doop use a single android.jar found
                // in its usual place. Use this flag together with an appropriate
                // value of the $PLATFORMS_LIB environment variable to provide a
                // custom android.jar.
                def completeAndroidJar = false
		// Set the following flag to true to have doop use a
		// custom set of JARs for Android. Use it with $PLATFORMS_LIB,
		// as with the 'completeAndroidJar' flag.
		def customJars = false
                if (completeAndroidJar) {
		    files = ["${path}/android.jar"]
		}
		else if (customJars) {
		    // Custom-built Android 4.4 libraries. These come from building the
		    // Android sources and looking in out/target/common/obj/JAVA_LIBRARIES.
		    files = ["${path}/bouncycastle_intermediates.jar",      // com.android.org.bouncycastle
                             "${path}/conscrypt_intermediates.jar",         // conscrypt
                             "${path}/core-junit_intermediates.jar",        // junit.framework
                             "${path}/core-libart_intermediates.jar",       // java.lang
                             "${path}/ext_intermediates.jar",               // com.android.i18n, org.apache.commons.codec.binary, org.apache.http, org.ccil.cowan.tagsoup
                             "${path}/framework_intermediates.jar",         // android.app, android.view, android.webkit
                             "${path}/libphotoviewer_appcompat_intermediates.jar", // android.support.v7.internal.widget
                             "${path}/okhttp_intermediates.jar",            // com.android.okhttp
                             "${path}/telephony-common_intermediates.jar",  // android.provider, android.telephony
			    ]
                }
                else {
                    switch(version) {
                        case 7:
                        case 15:
                            files = ["${path}/android.jar",
                                     "${path}/data/layoutlib.jar"]
                            break
                        case 16:
                            files = ["${path}/android.jar",
                                     "${path}/data/layoutlib.jar",
                                     "${path}/uiautomator.jar"]
                            break
                        case 17:
                        case 18:
                        case 19:
                        case 20:
                        case 21:
                        case 22:
                            files = ["${path}/android.jar",
                                     "${path}/data/icu4j.jar",
                                     "${path}/data/layoutlib.jar",
                                     "${path}/uiautomator.jar"]
                            break
                        case 23:
                            files = ["${path}/android.jar",
                                     "${path}/optional/org.apache.http.legacy.jar",
                                     "${path}/data/layoutlib.jar",
                                     "${path}/uiautomator.jar"]
                            break
                        case 24:
                            files = ["${path}/android.jar",
                                     "${path}/android-stubs-src.jar",
                                     "${path}/optional/org.apache.http.legacy.jar",
                                     "${path}/data/layoutlib.jar",
                                     "${path}/uiautomator.jar"]
                            break
                        default:
                            throw new RuntimeException("Invalid android version: $version")
                    }
                }
                break
            default:
                throw new RuntimeException("Invalid platform: $platform")
        }
        return files
    }

    /**
     * Processes the options of the analysis.
     */
    protected AnalysisVars processOptions(String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        logger.debug "Processing analysis options"

        def inputFilePaths = context.inputs()
        def platformFilePaths = platform(options)


        if (options.DISTINGUISH_ALL_STRING_BUFFERS.value &&
            options.DISTINGUISH_STRING_BUFFERS_PER_METHOD.value) {
            logger.warn "\nWARNING: multiple distinguish-string-buffer flags. 'All' overrides.\n"
        }

        if (!options.MERGE_LIBRARY_OBJECTS_PER_METHOD.value &&
            options.CONTEXT_SENSITIVE_LIBRARY_ANALYSIS.value) {
            logger.warn "\nWARNING, possible inconsistency: context-sensitive library analysis with merged objects.\n"
        }

        if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = false
        }

        if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = false
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
        }

        if (options.ENABLE_REFLECTION_CLASSIC.value) {
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = false
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.ENABLE_REFLECTION.value = true
            options.REFLECTION_SUBSTRING_ANALYSIS.value = true
            options.DISTINGUISH_STRING_BUFFERS_PER_METHOD.value = true
        }

        if (options.DACAPO.value) {
            def inputJarName = inputFilePaths[0]
            def deps = inputJarName.replace(".jar", "-deps.jar")
            if (!inputFilePaths.contains(deps))
                inputFilePaths.add(deps)

            if (!options.ENABLE_REFLECTION.value)
                options.TAMIFLEX.value = resolve([inputJarName.replace(".jar", "-tamiflex.log")])[0]

            def benchmark = FilenameUtils.getBaseName(inputJarName)
            logger.info "Running dacapo benchmark: $benchmark"
            options.DACAPO_BENCHMARK.value = benchmark
        }

        if (options.DACAPO_BACH.value) {
            def inputJarName = inputFilePaths[0]
            if (inputJarName.startsWith("http")) throw new RuntimeException("Currently, a local path is required for DaCapo-Bach benchmarks")
            def depsDir = inputJarName.replace(".jar", "-libs")
            new File(depsDir).eachFile { File depsFile ->
                if (FilenameUtils.getExtension(depsFile.getName()).equals("jar") && !inputFiles.contains(depsFile))
                    inputFiles.add(depsFile)
            }

            if (!options.ENABLE_REFLECTION.value)
                options.TAMIFLEX.value = inputJarName.replace(".jar", "-tamiflex.log")

            def benchmark = FilenameUtils.getBaseName(inputJarName)
            logger.info "Running dacapo-bach benchmark: $benchmark"
            options.DACAPO_BENCHMARK.value = benchmark
        }

        if (options.TAMIFLEX.value) {
            options.ENABLE_REFLECTION.value = false
        }

        if (options.MUST.value) {
            options.SSA.value = true
            options.CFG_ANALYSIS.value = true
            options.MUST_AFTER_MAY.value = true
        }

        context.resolve()
        def inputFiles = context.getAll()
        def platformFiles = resolve(platformFilePaths)


        if (options.MAIN_CLASS.value) {
            logger.debug "The main class is set to ${options.MAIN_CLASS.value}"
        } else {
            JarFile jarFile = new JarFile(inputFiles[0])
            //Try to read the main class from the manifest contained in the jar
            def main = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS)
            if (main) {
                logger.debug "The main class is automatically set to ${main}"
                options.MAIN_CLASS.value = main
            } else {
                //Check whether the jar contains a class with the same name
                def jarName = FilenameUtils.getBaseName(jarFile.getName())
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
        AnalysisVars vars = new AnalysisVars(
            name:              name,
            options:           options,
            inputFilePaths:    inputFilePaths,
            platformFilePaths: platformFilePaths,
            inputFiles:        inputFiles,
            platformFiles:     platformFiles
        )
        logger.debug vars
        logger.debug "---------------"

        return vars
    }

    List<File> resolve(List<String> filePaths) {
        def context = new DefaultInputResolutionContext()
        filePaths.each { f -> context.add(f) }
        context.resolve()
        return context.getAll()
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
            Set<String> packages = Helper.getPackages(vars.inputFiles[0])
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

        def lbHomeDir = FileOps.findDirOrThrow(lbhome.value as String, "The ${lbhome.id} value is invalid: ${lbhome.value}")

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
     *     <li>adding the LOGICBLOX_HOME option to the current environment
     *     <li>adding the DOOP_HOME to the current environment
     *     <li>adding the LB_PAGER_FORCE_START and the LB_MEM_NOWARN to the current environment
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
        //We add these LB specific env vars here to make the server deployment more flexible (and the cli user's life easier)
        env.LB_PAGER_FORCE_START = "true"
        env.LB_MEM_NOWARN = "true"
        env.DOOP_HOME = Doop.doopHome

        return env
    }
}
