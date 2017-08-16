package org.clyze.doop.core

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.*
import org.clyze.doop.input.DefaultInputResolutionContext
import org.clyze.doop.input.InputResolutionContext
import org.clyze.utils.CheckSum
import org.clyze.utils.FileOps
import org.clyze.utils.Helper

import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * A Factory for creating Analysis objects.
 *
 * All the methods invoked by newAnalysis (either directly or indirectly) could
 * have been static helpers (e.g. entailed in the Helper class) but they are
 * protected instance methods to allow descendants to customize all possible
 * aspects of Analysis creation.
 */
class DoopAnalysisFactory implements AnalysisFactory<DoopAnalysis> {

    Log logger = LogFactory.getLog(getClass())
    static final char[] EXTRA_ID_CHARACTERS = '_-+.'.toCharArray()
    static final String HASH_ALGO = "SHA-256"

    /**
     * A helper class that acts as an intermediate holder of the analysis variables.
     */
    protected static class AnalysisVars {
        String name
        Map<String, AnalysisOption> options
        List<String> inputFilePaths
        List<String> platformFilePaths
        List<File> inputFiles
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
     * Creates a new analysis, verifying the correctness of its id, name, options and inputFiles using
     * the supplied input resolution mechanism.
     * If the supplied id is empty or null, an id will be generated automatically.
     * Otherwise the id will be validated:
     * - if it is valid, it will be used to identify the analysis,
     * - if it is invalid, an exception will be thrown.
     */
    DoopAnalysis newAnalysis(String id, String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        def vars = processOptions(name, options, context)

        checkAnalysis(name)
        if (options.LB3.value)
            checkLogicBlox(vars)

        //init the environment used for executing commands
        Map<String, String> commandsEnv = initExternalCommandsEnvironment(vars)

        // if not empty or null
        def analysisId = id ? validateUserSuppliedId(id) : generateId(vars)

        def cacheId = generateCacheID(vars)

        def outDir = createOutputDirectory(vars, analysisId)

        def cacheDir

        if (options.X_START_AFTER_FACTS.value) {
            cacheDir = new File(options.X_START_AFTER_FACTS.value)
            FileOps.findDirOrThrow(cacheDir, "Invalid user-provided facts directory: $cacheDir")
        }
        else {
            cacheDir = new File("${Doop.doopCache}/$cacheId")
            checkAppGlob(vars)
        }

        DoopAnalysis analysis
        if (options.LB3.value) {
            if (name != "sound-may-point-to") {
                options.CFG_ANALYSIS.value = false
                analysis = new ClassicAnalysis(
                        analysisId,
                        name.replace(File.separator, "-"),
                        options,
                        context,
                        outDir,
                        cacheDir,
                        vars.inputFiles,
                        vars.platformFiles,
                        commandsEnv)
            } else {
                analysis = new SoundMayAnalysis(
                        analysisId,
                        name.replace(File.separator, "-"),
                        options,
                        context,
                        outDir,
                        cacheDir,
                        vars.inputFiles,
                        vars.platformFiles,
                        commandsEnv)
            }
        } else {
            options.CFG_ANALYSIS.value = false
            analysis = new SouffleAnalysis(
                    analysisId,
                    name.replace(File.separator, "-"),
                    options,
                    context,
                    outDir,
                    cacheDir,
                    vars.inputFiles,
                    vars.platformFiles,
                    commandsEnv)
        }
        logger.debug "Created new analysis"
        return analysis
    }

    /**
     * Creates a new analysis, verifying the correctness of its name, options and inputFiles using
     * the default input resolution mechanism.
     */
    @Override
    DoopAnalysis newAnalysis(AnalysisFamily family, String id, String name, Map<String, AnalysisOption> options, List<String> inputFilePaths) {
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
        idComponents = [vars.name] + vars.inputFilePaths + idComponents
        logger.debug("ID components: $idComponents")
        def id = idComponents.join('-')

        return CheckSum.checksum(id, HASH_ALGO)
    }

    protected String generateCacheID(AnalysisVars vars) {
        Collection<String> idComponents = vars.options.values()
                .findAll { it.forCacheID }
                .collect { option -> option.toString() }

        Collection<String> checksums = []
        checksums += vars.inputFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }
        checksums += vars.platformFiles.collect { file -> CheckSum.checksum(file, HASH_ALGO) }

        if (vars.options.TAMIFLEX.value && vars.options.TAMIFLEX.value != "dummy")
            checksums += [CheckSum.checksum(new File(vars.options.TAMIFLEX.value.toString()), HASH_ALGO)]

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
                if (platformInfo.size == 2) {
                    String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"
                    switch (version) {
                        case 3:
                            files = ["${path}/rt.jar"]
                            break
                        case 4:
                        case 5:
                        case 6:
                        case 8:
                            files = ["${path}/rt.jar",
                                     "${path}/jce.jar",
                                     "${path}/jsse.jar"]
                            break
                        case 7:
                            files = ["${path}/rt.jar",
                                     "${path}/jce.jar",
                                     "${path}/jsse.jar",
                                     "${path}/tools.jar"]
                            break
                        default:
                            throw new RuntimeException("Invalid JRE version: $version")
                    }
                }
                else if (platformInfo.size == 3 && platformInfo[2] == "debug") {
                    String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}_${platformInfo[2]}/lib"
                    switch (version) {
                        case 7:
                        case 8:
                            files = ["${path}/rt.jar",
                                     "${path}/jce.jar",
                                     "${path}/jsse.jar"]
                            break
                        default:
                            throw new RuntimeException("Invalid JRE version: $version")
                    }
                }
                else if (platformInfo.size == 3 && platformInfo[2] != "debug") {
                    String minorVersion = platformInfo[2]

                    switch (version) {
                        case 7:
                        case 8:
                            String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}.0_${minorVersion}/lib"
                            files = ["${path}/rt.jar",
                                     "${path}/jce.jar",
                                     "${path}/jsse.jar"]
                            break
                        default:
                            throw new RuntimeException("Invalid JRE version: $version")
                    }
                }
                else {
                    throw new RuntimeException("Invalid JRE version: $version")
                }
                // generate the JRE constant for the preprocessor
                def jreOption = new BooleanAnalysisOption(
                        id:"JRE1"+version,
                        value:true,
                        forPreprocessor: true
                )
                options[(jreOption.id)] = jreOption
                break
            case "android":
                if (platformInfo.size < 3)
                    throw new RuntimeException("Invalid Android platform: $platformInfo")
                // If the user has given a platform ending in
                // "_fulljars", then use the "full" subdirectory of
                // the platforms library, otherwise use the "stubs"
                // one. This permits use of two Android system JARs
                // side-by-side: either the stubs provided by the
                // official Android SDK or a custom Android build.
                if (platformInfo[2] != "stubs" && platformInfo[2] != "fulljars")
                    throw new RuntimeException("Invalid Android platform: $platformInfo")
                String androidLibFlavor = (platformInfo[2] == "fulljars" ? "full" : "stubs")
                String path = "${options.PLATFORMS_LIB.value}/Android/$androidLibFlavor/Android/Sdk/platforms/android-$version"
                options.ANDROID.value = true

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
                    case 25:
                        files = ["${path}/android.jar",
                                 "${path}/android-stubs-src.jar",
                                 "${path}/optional/org.apache.http.legacy.jar",
                                 "${path}/data/layoutlib.jar",
                                 "${path}/uiautomator.jar"]
                        break
                    default:
                        throw new RuntimeException("Invalid android version: $version")
                }
                break
            default:
                throw new RuntimeException("Invalid platform: $platform")
                // FIXME: When "full" JARs are used, pick only the first
                // one (assumed to be android.jar) or XML parsing fails.
                if (androidLibFlavor.equals("full"))
                    files = [ files[0] ]
        }
        return files
    }

    /**
     * Processes the options of the analysis.
     */
    protected AnalysisVars processOptions(String name, Map<String, AnalysisOption> options, InputResolutionContext context) {

        logger.debug "Processing analysis options"


        def inputFilePaths
        def platformFilePaths
        def inputFiles
        def platformFiles

        if (!options.X_START_AFTER_FACTS.value) {
            inputFilePaths = context.inputs()
            platformFilePaths = platform(options)

            context.resolve()
            inputFiles = context.getAll()
            platformFiles = resolve(platformFilePaths)
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            if (!options.X_START_AFTER_FACTS.value) {
                def inputJarName = inputFilePaths[0]
                def deps = inputJarName.replace(".jar", "-deps.jar")
                if (!inputFilePaths.contains(deps))
                    inputFilePaths.add(deps)

                if (!options.REFLECTION.value && !options.TAMIFLEX.value)
                    options.TAMIFLEX.value = resolve([inputJarName.replace(".jar", "-tamiflex.log")])[0]

                def benchmark = FilenameUtils.getBaseName(inputJarName)
                logger.info "Running " + (options.DACAPO.value ? "dacapo" : "dacapo-bach") + " benchmark: $benchmark"
            }
            else {
                options.TAMIFLEX.value = "dummy"
            }
        }

        if (options.MAIN_CLASS.value) {
            logger.debug "The main class is set to ${options.MAIN_CLASS.value}"
        } else {
            if (!options.X_START_AFTER_FACTS.value && !options.IGNORE_MAIN_METHOD.value) {
                JarFile jarFile = new JarFile(inputFiles[0])
                //Try to read the main class from the manifest contained in the jar
                def main = jarFile.getManifest()?.getMainAttributes()?.getValue(Attributes.Name.MAIN_CLASS)
                if (main) {
                    logger.debug "The main class is automatically set to ${main}"
                    options.MAIN_CLASS.value = main
                } else {
                    //Check whether the jar contains a class with the same name
                    def jarName = FilenameUtils.getBaseName(jarFile.getName())
                    if (jarFile.getJarEntry("${jarName}.class")) {
                        logger.debug "The main class is automatically set to ${jarName}"
                        options.MAIN_CLASS.value = jarName
                    } else {
                        logger.debug "\nWARNING: No main class was found. This will trigger open-program analysis!\n"
                    }
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

        if (options.TAMIFLEX.value && options.TAMIFLEX.value != "dummy") {
            def tamFile = options.TAMIFLEX.value.toString()
            FileOps.findFileOrThrow(tamFile, "The TAMIFLEX option is invalid: ${tamFile}")
        }


        if (options.DISTINGUISH_ALL_STRING_BUFFERS.value &&
                options.DISTINGUISH_STRING_BUFFERS_PER_PACKAGE.value) {
            logger.warn "\nWARNING: multiple distinguish-string-buffer flags. 'All' overrides.\n"
        }

        if (options.NO_MERGE_LIBRARY_OBJECTS.value) {
            options.MERGE_LIBRARY_OBJECTS_PER_METHOD.value = false
        }

        if (options.MERGE_LIBRARY_OBJECTS_PER_METHOD.value && options.CONTEXT_SENSITIVE_LIBRARY_ANALYSIS.value) {
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

        if (options.REFLECTION_CLASSIC.value) {
            options.DISTINGUISH_ALL_STRING_CONSTANTS.value = false
            options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value = true
            options.REFLECTION.value = true
            options.REFLECTION_SUBSTRING_ANALYSIS.value = true
            options.DISTINGUISH_STRING_BUFFERS_PER_PACKAGE.value = true
        }

        if (options.TAMIFLEX.value) {
            options.REFLECTION.value = false
        }

        if (options.MINIMAL_INFORMATION_FLOW.value) {
            options.INFORMATION_FLOW.value = options.MINIMAL_INFORMATION_FLOW.value
        }

        if (options.NO_SSA.value) {
            options.SSA.value = false
        }

        if (options.MUST.value) {
            options.MUST_AFTER_MAY.value = true
        }

        if (options.X_DRY_RUN.value) {
            options.X_STATS_NONE.value = true
            options.X_SERVER_LOGIC.value = true
            if (options.CACHE.value) {
                logger.warn "\nWARNING: Doing a dry run of the analysis while using cached facts might be problematic!\n"
            }
        }

        if (!options.REFLECTION.value) {
            if (options.DISTINGUISH_REFLECTION_ONLY_STRING_CONSTANTS.value ||
                    options.REFLECTION_SUBSTRING_ANALYSIS.value ||
                    options.REFLECTION_CONTEXT_SENSITIVITY.value ||
                    options.REFLECTION_HIGH_SOUNDNESS_MODE.value ||
                    options.REFLECTION_SPECULATIVE_USE_BASED_ANALYSIS.value ||
                    options.REFLECTION_INVENT_UNKNOWN_OBJECTS.value ||
                    options.REFLECTION_REFINED_OBJECTS.value) {
                logger.warn "\nWARNING: Probable inconsistent set of Java reflection flags!\n"
            } else if (options.TAMIFLEX.value) {
                logger.warn "\nWARNING: Handling of Java reflection via Tamiflex logic!\n"
            } else {
                logger.warn "\nWARNING: Handling of Java reflection is disabled!\n"
            }
        }

        options.values().each {
            if (it.argName && it.value && it.validValues && !(it.value in it.validValues))
                throw new RuntimeException("Invalid value `$it.value` for option: $it.name")
        }

        options.values().findAll { it.isMandatory }.each {
            if (!it.value) throw new RuntimeException("Missing mandatory argument: $it.name")
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
     *     <li>adding the variables/paths/tweaks to meet the lb-env-bin.sh requirements of the pa-datalog distro
     * </ul>
     */
    protected Map<String, String> initExternalCommandsEnvironment(AnalysisVars vars) {

        logger.debug "Initializing the environment of the external commands"

        Map<String, String> env = [:]
        env.putAll(System.getenv())

        String lbHome = vars.options.LOGICBLOX_HOME.value
        env.LOGICBLOX_HOME = lbHome
        //We add these LB specific env vars here to make the server deployment more flexible (and the cli user's life easier)
        env.LB_PAGER_FORCE_START = "true"
        env.LB_MEM_NOWARN = "true"
        env.DOOP_HOME = Doop.doopHome

        //We add the following for pa-datalog to function properly (copied from the lib-env-bin.sh script)
        //PATH
        String path = env.PATH
        if (path) {
            path = "${lbHome}/bin:${path}"
        }
        else {
            path = "${lbHome}/bin"
        }
        env.PATH = path

        //LD_LIBRARY_PATH
        String ldLibraryPath = vars.options.LD_LIBRARY_PATH.value
        if (ldLibraryPath) {
            ldLibraryPath = "${lbHome}/lib/cpp:${ldLibraryPath}"
        }
        else {
            ldLibraryPath = "${lbHome}/lib/cpp"
        }
        env.LD_LIBRARY_PATH = ldLibraryPath

        //LC_ALL
        env.LC_ALL = "en_US.UTF-8"

        return env
    }
}
