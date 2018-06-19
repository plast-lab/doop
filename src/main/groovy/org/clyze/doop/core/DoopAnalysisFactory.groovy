
package org.clyze.doop.core

import groovy.util.logging.Log4j
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.*
import org.clyze.doop.input.DefaultInputResolutionContext
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.utils.PackageUtil
import org.clyze.utils.CheckSum
import org.clyze.utils.FileOps

import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * A Factory for creating Analysis objects.
 *
 * [Note] All the methods invoked by newAnalysis (either directly or
 * indirectly) could have been static helpers (e.g. entailed in the
 * Helper class) but they are protected instance methods to allow
 * descendants to customize all possible aspects of Analysis creation.
 */
@Log4j
class DoopAnalysisFactory implements AnalysisFactory<DoopAnalysis> {

    static final char[] EXTRA_ID_CHARACTERS = '_-+.'.toCharArray()
    static final String HASH_ALGO = "SHA-256"
    static final Map<String, Set<String>> artifactsForPlatform =
            [ // JDKs
              "java_3" : ["rt.jar"],
              "java_4" : ["rt.jar", "jce.jar", "jsse.jar"],
              "java_5" : ["rt.jar", "jce.jar", "jsse.jar"],
              "java_6" : ["rt.jar", "jce.jar", "jsse.jar"],
              "java_7" : ["rt.jar", "jce.jar", "jsse.jar", "tools.jar"],
              "java_8" : ["rt.jar", "jce.jar", "jsse.jar"],
              // Android compiled from sources
              "android_22_fulljars" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
                                       "optional/org.apache.http.legacy.jar"],
              "android_25_fulljars" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
                                       "optional/org.apache.http.legacy.jar"],
              // Android API stubs (from the SDK)
              "android_7_stubs"  : ["android.jar", "data/layoutlib.jar"],
              "android_15_stubs" : ["android.jar", "data/layoutlib.jar"],
              "android_16_stubs" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_17_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_18_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_19_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_20_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_21_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar"],
              "android_22_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar"],
              "android_23_stubs" : ["android.jar", "data/icu4j.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar"],
              "android_24_stubs" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
              "android_25_stubs" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
              "android_26_stubs" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
              // Android-Robolectric
              "android_26_robolectric" : ["android.jar", "data/layoutlib.jar", "uiautomator.jar",
                                    "optional/org.apache.http.legacy.jar", "android-stubs-src.jar"],
              //Python
              "python"           :[],
            ]
    static final availableConfigurations = [
            "twophase-A" : "TwoPhaseAConfiguration",
            "twophase-B" : "TwoPhaseBConfiguration",
            "context-insensitive" : "ContextInsensitiveConfiguration",
            "context-insensitive-plus" : "ContextInsensitivePlusConfiguration",
            "context-insensitive-plusplus" : "ContextInsensitivePlusPlusConfiguration",
            "1-call-site-sensitive" : "OneCallSiteSensitiveConfiguration",
            "1-call-site-sensitive+heap" : "OneCallSiteSensitivePlusHeapConfiguration",
            "1-type-sensitive" : "OneTypeSensitiveConfiguration",
            "1-type-sensitive+heap" : "OneTypeSensitivePlusHeapConfiguration",
            "1-object-sensitive" : "OneObjectSensitiveConfiguration",
            "1-object-sensitive+heap" : "OneObjectSensitivePlusHeapConfiguration",
            "2-call-site-sensitive" : "TwoCallSiteSensitiveConfiguration",
            "2-call-site-sensitive+heap" : "TwoCallSiteSensitivePlusHeapConfiguration",
            "2-call-site-sensitive+2-heap" : "TwoCallSiteSensitivePlusTwoHeapConfiguration",
            "2-type-sensitive" : "TwoTypeSensitiveConfiguration",
            "2-type-sensitive+heap" : "TwoTypeSensitivePlusHeapConfiguration",
            "2-object-sensitive" : "TwoObjectSensitiveConfiguration",
            "2-object-sensitive+heap" : "TwoObjectSensitivePlusHeapConfiguration",
            "special-2-object-sensitive+heap" : "SpecialTwoObjectSensitivePlusHeapConfiguration",
            "special-2-type-sensitive+heap" : "SpecialTwoTypeSensitivePlusHeapConfiguration",
            "2-object-sensitive+2-heap" : "TwoObjectSensitivePlusTwoHeapConfiguration",
            "3-object-sensitive+3-heap" : "ThreeObjectSensitivePlusThreeHeapConfiguration",
            "2-type-object-sensitive+heap" : "TwoObjectSensitivePlusHeapConfiguration",
            "2-type-object-sensitive+2-heap" : "TwoObjectSensitivePlusTwoHeapConfiguration",
            "3-type-sensitive+2-heap" : "ThreeTypeSensitivePlusTwoHeapConfiguration",
            "3-type-sensitive+3-heap" : "ThreeTypeSensitivePlusThreeHeapConfiguration",
            "selective-2-object-sensitive+heap" : "SelectiveTwoObjectSensitivePlusHeapConfiguration",
            "partitioned-2-object-sensitive_heap" : "PartitionedTwoObjectSensitivePlusHeapConfiguration",
    ]

    /**
     * A helper class that acts as an intermediate holder of the analysis variables.
     */
    protected static class AnalysisVars {
        String name
        Map<String, AnalysisOption> options
        List<String> inputFilePaths
        List<String> libraryFilePaths
        List<String> heapdlFilePaths
        List<String> platformFilePaths
        List<File>   inputFiles
        List<File>   libraryFiles
        List<File>   heapFiles
        List<File>   platformFiles

        @Override
        String toString() {
            return [
                    name:              name,
                    options:           options.values().toString(),
                    inputFilePaths:    inputFilePaths.toString(),
                    libraryFilePaths:  libraryFilePaths.toString(),
                    platformFilePaths: platformFilePaths.toString(),
                    inputFiles:        inputFiles.toString(),
                    libraryFiles:      libraryFiles.toString(),
                    heapFiles:         heapFiles.toString(),
                    platformFiles:     platformFiles.toString()
            ].toString()
        }
    }

    private static getConfiguration(String analysisName) {
        return availableConfigurations.get(analysisName)
    }

    /**
     * Creates a new analysis, verifying the correctness of its id, name, options and inputFiles using
     * the supplied input resolution mechanism.
     * If the supplied id is empty or null, an id will be generated automatically.
     * Otherwise the id will be validated:
     * - if it is valid, it will be used to identify the analysis,
     * - if it is invalid, an exception will be thrown.
     */
    DoopAnalysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, InputResolutionContext context) {
        options.CONFIGURATION.value = getConfiguration(name)

        def vars = processOptions(name, options, context)

        checkAnalysis(name, options)
        if (options.LB3.value)
            checkLogicBlox(vars)

        //init the environment used for executing commands
        Map<String, String> commandsEnv = initExternalCommandsEnvironment(vars)

        // if not empty or null
        def analysisId = id ? validateUserSuppliedId(id) : generateId(vars)

        def outDir = createOutputDirectory(vars, analysisId)

        def cacheDir

        if (options.X_START_AFTER_FACTS.value) {
            cacheDir = new File(options.X_START_AFTER_FACTS.value as String)
            FileOps.findDirOrThrow(cacheDir, "Invalid user-provided facts directory: $cacheDir")
        }
        else {
            def cacheId = generateCacheID(vars)
            cacheDir = new File("${Doop.doopCache}/$cacheId")
            checkAppGlob(vars)
        }

        DoopAnalysis analysis
        if (options.LB3.value) {
            analysis = new LB3Analysis(
                    analysisId,
                    name.replace(File.separator, "-"),
                    options,
                    context,
                    outDir,
                    cacheDir,
                    vars.inputFiles,
                    vars.libraryFiles,
                    vars.heapFiles,
                    vars.platformFiles,
                    commandsEnv)
        } else {
            analysis = new SouffleAnalysis(
                    analysisId,
                    name.replace(File.separator, "-"),
                    options,
                    context,
                    outDir,
                    cacheDir,
                    vars.inputFiles,
                    vars.libraryFiles,
                    vars.heapFiles,
                    vars.platformFiles,
                    commandsEnv)
        }
        log.debug "Created new analysis"
        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputFiles using
     * the default input resolution mechanism.
     */
    @Override
    DoopAnalysis newAnalysis(AnalysisFamily family, String id, String name, Map<String, AnalysisOption> options, List<String> inputFilePaths, List<String> libraryFilePaths, List<String> hprofFilePaths) {
        DefaultInputResolutionContext context = new DefaultInputResolutionContext()
        context.add(inputFilePaths, InputType.INPUT)
        context.add(libraryFilePaths, InputType.LIBRARY)
        context.add(hprofFilePaths, InputType.HPROF)
        return newAnalysis(id, name, options, context)
    }

    protected void checkAnalysis(String name, Map<String, AnalysisOption> options) {
        log.debug "Verifying analysis name: $name"
        def analysisPath
        if (options.LB3.value) 
          analysisPath = "${Doop.analysesPath}/${name}/analysis.logic"
        else
          analysisPath = "${Doop.souffleAnalysesPath}/${name}/analysis.dl"
        FileOps.findFileOrThrow(analysisPath, "Unsupported analysis: $name")
    }

    // This method may not be static, see [Note] above.
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

    // This method may not be static, see [Note] above.
    protected File createOutputDirectory(AnalysisVars vars, String id) {
        def outDir = new File("${Doop.doopOut}/${vars.name}/${id}")
        FileUtils.deleteQuietly(outDir)
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
        idComponents = [vars.name] + vars.inputFilePaths + vars.libraryFilePaths + idComponents
        log.debug "ID components: $idComponents"
        def id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    protected String generateCacheID(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.values()
                .findAll { it.forCacheID }
                .collect { option -> option.toString() }

        Collection<String> checksums = []
        checksums += vars.inputFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }
        checksums += vars.libraryFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }
        checksums += vars.heapFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }
        checksums += vars.platformFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }

        if (vars.options.TAMIFLEX.value && vars.options.TAMIFLEX.value != "dummy")
            checksums += [CheckSum.checksum(new File(vars.options.TAMIFLEX.value.toString()), HASH_ALGO)]

        idComponents = checksums + idComponents

        log.debug "Cache ID components: $idComponents"
        def id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    /**
     * Break up a platform name into its components. Examples:
     * "java_8" -> ["java", 8, null] and
     * "android_25_fulljars" -> ["android", 25, "fulljars"].
     *
     * @param platformName   Platform name.
     * @return               The platform components.
     */
    private static List tokenizePlatform(String platformName) {
        if(platformName == "python")
            return [platformName, "", ""]
        def platformInfo = platformName.tokenize("_")
        int partsCount = platformInfo.size()
        if ((partsCount != 2) && (partsCount != 3)) {
            throw new RuntimeException("Invalid platform: ${platformName}")
        }
        String platform = platformInfo[0]
        int version = platformInfo[1].toInteger()
        String variant = partsCount == 3 ? platformInfo[2] : null
        if ((platform == "android") && (variant == null)) {
            throw new RuntimeException("Invalid Android platform: ${platformInfo}")
        }
        return [platform, version, variant]
    }

    /**
     * Generates a list of the platform library arguments for Soot
     * (file paths of .jar archives).
     *
     * @param platformName    The name of the platform (e.g., "java_8").
     * @param platformsLib    The path of the Doop platforms directory.
     * @return                The list of artifact paths for the platform.
     */
    static List<String> getArtifactsForPlatform(String platformName, String platformsLib) {
        def (platform, version, variant) = tokenizePlatform(platformName)
        switch (platform) {
            case "java":
                if (variant == null) {
                    return getArtifactsForJava(platformName, version, platformsLib)
                }
                else {
                    // Custom support for minor versions installed
                    // locally, e.g., "java_8_debug".
                    String platformPath = "${platformsLib}/JREs/jre1.${version}.0_${variant}/lib"
                    if (!((new File(platformPath)).exists())) {
                        throw new RuntimeException("Minor-version platform does not exist: ${platformName}")
                    }
                    return getArtifactsForPlatformWithPath(platformName, platformPath)
                }
            case "android":
                if (![ "stubs", "fulljars", "robolectric" ].contains(variant)) {
                    throw new RuntimeException("Invalid Android platform: ${platformName}")
                }
                String path = "${platformsLib}/Android/${variant}/Android/Sdk/platforms/android-${version}"
                List platformArtifactPaths = getArtifactsForPlatformWithPath(platformName, path)
                if (variant == "robolectric") {
                    String roboJRE = "java_8"
                    println "Using ${roboJRE} with Robolectric"
                    def files = getArtifactsForJava(roboJRE, 8, platformsLib)
                    platformArtifactPaths.addAll(files)
                }
                return platformArtifactPaths
            case "python":
                return new ArrayList<String>(0)
            default:
                throw new RuntimeException("Invalid platform: ${platform}")
        }
    }

    /**
     * Set options according to the platform used. This functionality
     * is independent of fact generation and is used to turn on
     * preprocessor flags in the analysis logic.
     *
     * @param options        the Doop options to affect
     * @param platformName   the platform ("java_8", "android_25_fulljars")
     */
    private static void setOptionsForPlatform(Map<String, AnalysisOption> options,
                                              String platformName) {
        def (platform, version, variant) = tokenizePlatform(platformName)
        if (platform == "java") {
            // generate the JRE constant for the preprocessor
            def jreOption = new BooleanAnalysisOption(
                id:"JRE1"+version,
                value:true,
                forPreprocessor: true
            )
            options[(jreOption.id)] = jreOption
        } else if (platform == "android") {
            options.ANDROID.value = true
        }else if (platform == "python") {
            options.PYTHON.value = true
        } else {
            throw new RuntimeException("No options for ${platformName}")
        }
    }

    /**
     * Processes the options of the analysis.
     */
    protected AnalysisVars processOptions(String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        log.debug "Processing analysis options"

        String platformName = options.PLATFORM.value.toString()
        String platformsLib = options.PLATFORMS_LIB.value.toString()
        def inputFilePaths
        def libraryFilePaths
        def heapdlFilePaths
        def platformFilePaths
        def inputFiles
        def libraryFiles
        def heapFiles
        def platformFiles

        if (!options.X_START_AFTER_FACTS.value) {
            inputFilePaths = context.inputs()            
            libraryFilePaths = context.libraries()
            heapdlFilePaths = context.hprofs()
            platformFilePaths = getArtifactsForPlatform(platformName, platformsLib)

            log.debug "Resolving inputs and libraries"
            context.resolve()
            inputFiles = context.getAllInputs()
            log.debug "Input file paths: $inputFilePaths -> $inputFiles"
            libraryFiles = context.getAllLibraries()
            log.debug "Library file paths: $libraryFilePaths -> $libraryFiles"
            heapFiles = context.getAllHprofs()
            log.debug "HeapDL file paths: $heapdlFilePaths -> $heapFiles"
            platformFiles = resolve(platformFilePaths, InputType.LIBRARY)
            log.debug "Platform file paths: $platformFilePaths -> $platformFiles"
        }

        setOptionsForPlatform(options, platformName)

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            if (!options.X_START_AFTER_FACTS.value) {
                def inputJarName = inputFilePaths[0]
                def deps = inputJarName.replace(".jar", "-deps.jar")
                if (!inputFilePaths.contains(deps) && !libraryFilePaths.contains(deps)) {
                    libraryFilePaths.add(deps)
                    context.resolve()
                    libraryFiles = context.getAllLibraries()
                }

                if (!options.REFLECTION.value && !options.TAMIFLEX.value)
                    options.TAMIFLEX.value = resolve([inputJarName.replace(".jar", "-tamiflex.log")], InputType.INPUT)[0]

                def benchmark = FilenameUtils.getBaseName(inputJarName)
                log.info "Running ${options.DACAPO.value ? "dacapo" : "dacapo-bach"} benchmark: $benchmark"
            }
            else {
                options.TAMIFLEX.value = "dummy"
            }
        }
        if(! options.PYTHON.value) {
            if (options.MAIN_CLASS.value) {
                if (options.X_START_AFTER_FACTS.value &&
                        options.X_SYMLINK_CACHED_FACTS.value) {
                    throw new RuntimeException("Option --${options.MAIN_CLASS.name} is not compatible with --${options.X_START_AFTER_FACTS.name} when using symbolic links")
                } else if (options.IGNORE_MAIN_METHOD.value) {
                    throw new RuntimeException("Option --${options.MAIN_CLASS.name} is not compatible with --${options.IGNORE_MAIN_METHOD.name}")
                } else {
                    log.debug "The main class is set to ${options.MAIN_CLASS.value}"
                }
            } else {
                if (!options.X_START_AFTER_FACTS.value && !options.IGNORE_MAIN_METHOD.value) {
                    if (inputFiles[0] == null) {
                        throw new RuntimeException("Error: no input files")
                    }
                    JarFile jarFile = new JarFile(inputFiles[0])
                    //Try to read the main class from the manifest contained in the jar
                    def main = jarFile.getManifest()?.getMainAttributes()?.getValue(Attributes.Name.MAIN_CLASS)
                    if (main) {
                        log.debug "The main class is automatically set to ${main}"
                        options.MAIN_CLASS.value = main
                    } else {
                        //Check whether the jar contains a class with the same name
                        def jarName = FilenameUtils.getBaseName(jarFile.getName())
                        if (jarFile.getJarEntry("${jarName}.class")) {
                            log.debug "The main class is automatically set to ${jarName}"
                            options.MAIN_CLASS.value = jarName
                        }
                    }
                }
            }
        }


        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            dynFiles.each { String dynFile ->
                FileOps.findFileOrThrow(dynFile, "The DYNAMIC option is invalid: ${dynFile}")
                log.debug "The DYNAMIC option has been set to ${dynFile}"
            }
        }

        if (options.TAMIFLEX.value && options.TAMIFLEX.value != "dummy") {
            def tamFile = options.TAMIFLEX.value.toString()
            FileOps.findFileOrThrow(tamFile, "The TAMIFLEX option is invalid: ${tamFile}")
        }

        if (options.DISTINGUISH_ALL_STRING_BUFFERS.value &&
                options.DISTINGUISH_STRING_BUFFERS_PER_PACKAGE.value) {
            log.warn "\nWARNING: multiple distinguish-string-buffer flags. 'All' overrides.\n"
        }

        if (options.NO_MERGE_LIBRARY_OBJECTS.value) {
            options.MERGE_LIBRARY_OBJECTS_PER_METHOD.value = false
        }

        if (options.MERGE_LIBRARY_OBJECTS_PER_METHOD.value && options.CONTEXT_SENSITIVE_LIBRARY_ANALYSIS.value) {
            log.warn "\nWARNING, possible inconsistency: context-sensitive library analysis with merged objects.\n"
        }

        if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value &&
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            throw new RuntimeException("Error: options " + options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.name + " and " + options.DISTINGUISH_ALL_STRING_CONSTANTS.name + " are mutually exclusive.\n")
        }

        if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value) {
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = false
        }

        if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = false
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = true
        }

        if (options.REFLECTION_CLASSIC.value) {
            if (options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
                throw new RuntimeException("Error: options " + options.REFLECTION_CLASSIC.name + " and " + options.DISTINGUISH_ALL_STRING_CONSTANTS.name + " are mutually exclusive.\n")
            }
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = false
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.REFLECTION.value = true
            options.REFLECTION_SUBSTRING_ANALYSIS.value = true
            options.DISTINGUISH_STRING_BUFFERS_PER_PACKAGE.value = true
            options.TAMIFLEX.value = null
        }

        if (options.LIGHT_REFLECTION_GLUE.value && options.REFLECTION.value) {
            throw new RuntimeException("Error: option " + options.LIGHT_REFLECTION_GLUE.name + " is not supported when reflection support is enabled.")
        }

        if (options.TAMIFLEX.value) {
            options.REFLECTION.value = false
        }

        if (options.NO_SSA.value) {
            options.SSA.value = false
        }

        if (options.MUST.value) {
            options.MUST_AFTER_MAY.value = true
        }

        if (!options.MAIN_CLASS.value && !options.TAMIFLEX.value &&
            !options.HEAPDL.value && !options.ANDROID.value &&
            !options.DACAPO.value && !options.DACAPO_BACH.value &&
            !options.X_START_AFTER_FACTS.value) {
            log.debug "\nWARNING: No main class was found. This will trigger open-program analysis!\n"
            if (!options.OPEN_PROGRAMS.value)
                options.OPEN_PROGRAMS.value = "concrete-types"
        }

        if (options.X_DRY_RUN.value) {
            options.X_STATS_NONE.value = true
            options.X_SERVER_LOGIC.value = true
            if (options.CACHE.value) {
                log.warn "\nWARNING: Doing a dry run of the analysis while using cached facts might be problematic!\n"
            }
        }

        if (options.APP_REGEX.value &&
            options.AUTO_APP_REGEX_MODE.value) {
            throw new RuntimeException("Error: options " + options.APP_REGEX.name + " and " + options.AUTO_APP_REGEX_MODE.name + " are mutually exclusive.\n")
        }

        // If server mode is enabled, don't produce statistics.
        if (options.X_SERVER_LOGIC.value) {
            options.X_STATS_FULL.value = false
            options.X_STATS_DEFAULT.value = false
            options.X_STATS_NONE.value = true
        }

        // If no stats option is given, select default stats.
        if (!options.X_STATS_FULL.value && !options.X_STATS_DEFAULT.value &&
            !options.X_STATS_NONE.value && !options.X_STATS_AROUND.value) {
            options.X_STATS_DEFAULT.value = true
        }

        if (options.REFLECTION_DYNAMIC_PROXIES.value && !options.REFLECTION.value) {
            String message = "\nWARNING: Dynamic proxy support without standard reflection support, using custom 'opt-reflective' reflection rules."
            if (!options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value &&
                !options.DISTINGUISH_ALL_STRING_CONSTANTS.value) {
                message += "\nWARNING: 'opt-reflective' may not work optimally, one of these flags is suggested: --" + options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.name + ", --" + options.DISTINGUISH_ALL_STRING_CONSTANTS.name
            }
            log.warn message
        }

        if (!options.REFLECTION.value) {
            if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value ||
                    options.REFLECTION_SUBSTRING_ANALYSIS.value ||
                    options.REFLECTION_CONTEXT_SENSITIVITY.value ||
                    options.REFLECTION_HIGH_SOUNDNESS_MODE.value ||
                    options.REFLECTION_SPECULATIVE_USE_BASED_ANALYSIS.value ||
                    options.REFLECTION_INVENT_UNKNOWN_OBJECTS.value ||
                    options.REFLECTION_REFINED_OBJECTS.value) {
                log.warn "\nWARNING: Probable inconsistent set of Java reflection flags!\n"
            } else if (options.TAMIFLEX.value) {
                log.warn "\nWARNING: Handling of Java reflection via Tamiflex logic!\n"
            } else {
                log.warn "\nWARNING: Handling of Java reflection is disabled!\n"
            }
        }

        options.values().each {
            if (it.argName && it.value && it.validValues && !(it.value in it.validValues))
                // An unknown platform are not always an error: it may
                // be a local subdirectory under doop-benchmarks.
                if (it.id == "PLATFORM") {
                    log.warn "\nWARNING: Non-standard platform selected: ${it.value}\n"
                } else {
                    throw new RuntimeException("Invalid value `$it.value` for option: $it.name")
                }
        }

        options.values().findAll { it.isMandatory }.each {
            if (!it.value) throw new RuntimeException("Missing mandatory argument: $it.name")
        }

        log.debug "---------------"
        AnalysisVars vars = new AnalysisVars(
                name:              name,
                options:           options,
                inputFilePaths:    inputFilePaths,
                libraryFilePaths:  libraryFilePaths,
                platformFilePaths: platformFilePaths,
                inputFiles:        inputFiles,
                libraryFiles:      libraryFiles,
                heapFiles:         heapFiles,
                platformFiles:     platformFiles
        )
        log.debug vars
        log.debug "---------------"

        return vars
    }

    static List<File> resolve(List<String> filePaths, InputType inputType) {
        def context = new DefaultInputResolutionContext()
        filePaths.each { f -> context.add(f, inputType) }
        context.resolve()
        switch (inputType) {
        case InputType.LIBRARY: return context.getAllLibraries()
        case InputType.INPUT: return context.getAllInputs()
        case InputType.HPROF: return context.getAllHprofs()
        default: throw new RuntimeException("Unknown inputType to resolve: ${inputType}")
        }
    }

    static List<File> resolveHeapdlInputs(List<String> filePaths) {
        List<File> ret = filePaths.collect { path -> new File(path) }
        File f = ret.find { !it.exists() }
        if (f)
            throw new RuntimeException("HeapDL input does not exist: ${f.canonicalPath}")
        return ret
    }

    /**
     * Determines application classes.
     *
     * If an app regex is not present, it generates one.
     */
    protected void checkAppGlob(AnalysisVars vars) {
        if (!vars.options.APP_REGEX.value) {
            log.debug "Generating app regex"

            //We process only the first jar for determining the application classes
            /*
            Set excluded = ["*", "**"] as Set
            analysis.jars.drop(1).each { Dependency jar ->
                excluded += PackageUtil.getPackages(jar.input())
            }

            Set<String> packages = PackageUtil.getPackages(analysis.jars[0].input()) - excluded
            */

            Set<String> packages
            String mode = vars.options.AUTO_APP_REGEX_MODE.value
            // Default is 'all'.
            if ((mode == null) || (mode == 'all')) {
                packages = [] as Set
                vars.inputFiles.each { packages.addAll(PackageUtil.getPackages(it)) }
            } else if (mode == 'first') {
                packages = PackageUtil.getPackages(vars.inputFiles[0])
            } else {
                throw new RuntimeException("Invalid auto-app-regex mode: ${mode}")
            }

            vars.options.APP_REGEX.value = packages.sort().join(':')

            log.debug "APP_REGEX: ${vars.options.APP_REGEX.value}"
        }
    }

    /**
     * Verifies the correctness of the LogicBlox related options
     */
    protected void checkLogicBlox(AnalysisVars vars) {

        //BLOX_OPTS is set by the main method

        AnalysisOption lbhome = vars.options.LOGICBLOX_HOME

        log.debug "Verifying LogicBlox home: ${lbhome.value}"

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
     *     <li>adding the variables/paths/tweaks to meet the lb-env-bin.sh requirements of the pa-datalog distro
     * </ul>
     */
    protected Map<String, String> initExternalCommandsEnvironment(AnalysisVars vars) {

        log.debug "Initializing the environment of the external commands"

        Map<String, String> env = [:]
        env.putAll(System.getenv())

        env.LC_ALL = "en_US.UTF-8"

        if (vars.options.LB3.value) {
            String lbHome = vars.options.LOGICBLOX_HOME.value
            env.LOGICBLOX_HOME = lbHome
            //We add these LB specific env vars here to make the server deployment more flexible (and the cli user's life easier)
            env.LB_PAGER_FORCE_START = "true"
            env.LB_MEM_NOWARN = "true"
            env.DOOP_HOME = Doop.doopHome

            //We add the following for pa-datalog to function properly (copied from the lib-env-bin.sh script)
            String path = env.PATH
            env.PATH = "${lbHome}/bin:${path ?: ""}" as String

            String ldLibraryPath = vars.options.LD_LIBRARY_PATH.value
            env.LD_LIBRARY_PATH = "${lbHome}/lib/cpp:${ldLibraryPath ?: ""}" as String
        }

        return env
    }

    /**
     * Returns a set of all the available analysis platforms
     *
     * @return the set of the available platforms
     */
    static final Set<String> getAvailablePlatforms() {
        return artifactsForPlatform.keySet() as Set<String>
    }

    /**
     * Returns the files needed to resolve a platform. This is a
     * helper method; to get the artifacts for a platform, use
     *
     * @param platformName   the platform ("java_8")
     * @param path           the path of the artifacts
     */
    private static final List<String> getArtifactsForPlatformWithPath(String platformName, String path) {
        List artifacts = artifactsForPlatform.get(platformName)
        if (artifacts == null) {
            throw new RuntimeException("Could not find artifacts for platform: ${platformName}")
        }
        return artifacts.collect { "${path}/${it}" }.collect { fname ->
            File f = new File(fname)
            if (!f.exists()) {
                throw new RuntimeException("Missing artifact: ${fname}")
            }
            f.canonicalPath
        }
    }

    private static final List<String> getArtifactsForJava(String platformName, Integer version, String platformsLib) {
        String platformPath = "${platformsLib}/JREs/jre1.${version}/lib/"
        return getArtifactsForPlatformWithPath(platformName, platformPath)
    }
}
