package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import heapdl.core.MemoryAnalyser
import org.apache.commons.io.FilenameUtils
import org.apache.log4j.Logger
import org.clyze.analysis.Analysis
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.common.CHA
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.util.ClassPathHelper
import org.clyze.doop.util.Resource
import org.clyze.doop.utils.CPreprocessor
import org.clyze.input.InputResolutionContext
import org.clyze.utils.*
import org.codehaus.groovy.runtime.StackTraceUtils

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.apache.commons.io.FileUtils.*

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 */
@CompileStatic
@Log4j
abstract class DoopAnalysis extends Analysis implements Runnable {

    /**
     * The facts dir for the input facts
     */
    protected File factsDir

    /**
     * The underlying workspace
     */
    protected File database

    /**
     * The analysis input resolution mechanism
     */
    InputResolutionContext ctx

    /**
     * Used for invoking external commands
     */
    protected Executor executor

    /**
     * Used for invoking the C preprocessor
     */
    protected CPreprocessor cpp

    /**
     * Total time for the fact generator invocation
     */
    protected long factGenTime

    /**
     * The suffix of information flow platforms.
     */
    static final String INFORMATION_FLOW_SUFFIX = "-sources-and-sinks"

    @Override
    String getId() { options.USER_SUPPLIED_ID.value as String }

    String getName() { options.ANALYSIS.value.toString().replace(File.separator, "-") }

    File getOutDir() { options.OUT_DIR.value as File }

    File getCacheDir() { options.CACHE_DIR.value as File }

    List<File> getInputFiles() { options.INPUTS.value as List<File> }

    List<File> getLibraryFiles() { options.LIBRARIES.value as List<File> }

    File getDatabase() { database }

    FactGenerator0 gen0

    /*
     * Use a java-way to construct the instance (instead of using Groovy's automatically generated Map constructor)
     * in order to ensure that internal state is initialized at one point and the init method is no longer required.
     */

    protected DoopAnalysis(Map<String, AnalysisOption<?>> options,
                           InputResolutionContext ctx,
                           Map<String, String> commandsEnvironment) {
        super(new DoopAnalysisFamily(), options)
        this.ctx = ctx

        if (!options.INPUT_ID.value) {
            log.info "New $name analysis"
            log.info "Id       : $id"
            log.info "Inputs   : ${inputFiles.join(', ')}"
            log.info "Libraries: ${libraryFiles.join(', ')}"
        } else
            log.info "New $name analysis on user-provided facts at ${options.INPUT_ID.value} - id: $id"

        database = new File(outDir, "database")
        database.mkdirs()

        factsDir = database
        gen0 = new FactGenerator0(factsDir)

        executor = new Executor(outDir, commandsEnvironment)
        cpp = new CPreprocessor(this, executor)

        new File(outDir, "meta").withWriter { it.write(this.toString()) }
    }

    String toString() {
        return options.values().collect { AnalysisOption option ->
            def v = option.value
            return "${option.id}=${v instanceof List ? (v as List).join(" ") : v as String}"
        }.sort().join("\n") + "\n"
    }

    @Override
    abstract void run()

    /**
     * Copies (or makes a symbolic link of) facts from an existing
     * directory to the "facts" directory of an analysis. Used both
     * when reading cached facts and when starting an analysis from
     * existing facts.
     *
     * @param fromDir the existing directory containing the facts
     */
    protected void linkOrCopyFacts(File fromDir) {
        if (options.X_SYMLINK_INPUT_FACTS.value) {
            try {
                fromDir.eachFile { file ->
                    Files.createSymbolicLink(new File(factsDir, file.name).toPath(), file.toPath())
                }
                return
            } catch (UnsupportedOperationException ignored) {
                log.warn("WARNING: Filesystem does not support symbolic links, copying directory instead...")
            }
        }

        if (fromDir != database)
            deleteQuietly(factsDir)
        factsDir.mkdirs()
        log.debug "Copying: ${fromDir} -> ${factsDir}"
        FileOps.copyDirContents(fromDir, factsDir)
    }

    /**
     * Generates the facts that do not need a call to the front end. Such
     * facts can also be written on top of reused facts.
     */
    protected void generateFacts0() {

        gen0.touch()

        if (options.KEEP_SPEC.value)
            gen0.writeKeepSpec(options.KEEP_SPEC.value as String)

        if (!options.PYTHON.value && options.MAIN_CLASS.value)
            gen0.writeMainClassFacts(options.MAIN_CLASS.value)

        if (options.INFORMATION_FLOW_EXTRA_CONTROLS.value)
            gen0.writeExtraSensitiveControls(options.INFORMATION_FLOW_EXTRA_CONTROLS.value.toString())

        if (options.DACAPO.value) {
            def benchmark = FilenameUtils.getBaseName(inputFiles[0].toString())
            def benchmarkCap = (benchmark as String).toLowerCase().capitalize()
            gen0.writeDacapoFacts(benchmark, benchmarkCap)
        } else if (options.DACAPO_BACH.value) {
            def benchmark = FilenameUtils.getBaseName(inputFiles[0].toString())
            def benchmarkCap = (benchmark as String).toLowerCase().capitalize()
            gen0.writeDacapoBachFacts(benchmarkCap)
        }

        if (options.TAMIFLEX.value) {
            File origTamFile = new File(options.TAMIFLEX.value.toString())
            gen0.writeTamiflexFacts(origTamFile)
        }

        if (options.X_EXTRA_FACTS.value) {
            for (String extraFactsPath : options.X_EXTRA_FACTS.value as Collection<String>) {
                File extraFacts = new File(extraFactsPath)
                if (extraFacts.exists()) {
                    log.info "Augmenting facts with file: ${extraFactsPath}"
                    Files.copy(extraFacts.toPath(), new File(database, extraFacts.name).toPath())
                } else
                    log.warn "WARNING: Facts file does not exist: ${extraFactsPath}"
            }

        }
    }

    /**
     * Initializes the facts directory. This method may be called repeatedly,
     * when restarting fact generation.
     */
    protected void initFactsDir() {
        deleteQuietly(factsDir)
        factsDir.mkdirs()
        generateFacts0()
    }

    protected void generateFacts() throws DoopErrorCodeException {

        if (options.CACHE.value) {
            log.info "Using cached facts from $cacheDir"
            linkOrCopyFacts(cacheDir)
        } else if (options.INPUT_ID.value) {
            def importedFactsDir = options.INPUT_ID.value as File
            log.info "Using user-provided facts from ${importedFactsDir} in ${factsDir}"
            linkOrCopyFacts(importedFactsDir)
        } else {

            log.info "-- Fact Generation --"
            initFactsDir()

            if (options.RUN_JPHANTOM.value) {
                runJPhantom()
            }

            Set<String> tmpDirs = [] as Set
            try {
                if (options.PYTHON.value) {
                    runPython(tmpDirs)
                } else if (options.WALA_FACT_GEN.value) {
                    runFrontEnd(tmpDirs, FrontEnd.WALA, null)
                } else if (options.X_DEX_FACT_GEN.value) {
                    runFrontEnd(tmpDirs, FrontEnd.DEX, new CHA())
                    // // Step 1. Run Soot in platform-only mode for fact
                    // // generation, to fill in non .dex facts and the type
                    // // hierarchy. Pur results in <factsDir>.
                    // options.X_FACTS_SUBSET.value = "PLATFORM"
                    // runFrontEnd(tmpDirs, FrontEnd.SOOT, null)
                    // // Step 2. Read CHA information from Soot run.
                    // CHA cha = new CHA()
                    // gen0.fillCHAFromSootFacts(cha)
                    // // Step 3. Run Dex front end with CHA information.
                    // File origFactsDir = factsDir
                    // File nonSSAFactsDir = new File("${factsDir}/nonSSA")
                    // log.info "Creating non-SSA facts directory ${nonSSAFactsDir}"
                    // nonSSAFactsDir.mkdirs()
                    // factsDir = nonSSAFactsDir
                    // options.X_FACTS_SUBSET.value = null
                    // runFrontEnd(tmpDirs, FrontEnd.DEX, cha)
                    // factsDir = origFactsDir
                    // // Step 4. Run SSA transformation in nonSSAFactsDir,
                    // // output in ssaFactsDir.
                    // // ...
                    // // Step 5. Merge factsDir + ssaFactsDir. This can also be
                    // // done in Datalog, either as merged output in step 4 or
                    // // with extra support in import-facts.dl.
                    // // ...
                } else {
                    runFrontEnd(tmpDirs, FrontEnd.SOOT, null)
                }
            } catch (all) {
                all = StackTraceUtils.deepSanitize all
                log.info all
                throw DoopErrorCodeException.error8(all)
            } finally {
                JHelper.cleanUp(tmpDirs)
            }

            if (options.UNIQUE_FACTS.value) {
                def timing = Helper.timing {
                    factsDir.eachFileMatch(~/.*.facts/) { file ->
                        def uniqueLines = file.readLines() as SortedSet<String>
                        uniqueLines.sort()
                        def tmp = new File(factsDir, "${file.name}.tmp")
                        tmp.withWriter { w -> uniqueLines.each { w.writeLine(it) } }
                        tmp.renameTo(file)
                    }
                }
                log.info "Time to make facts unique: $timing"
            }

            if (!options.INPUT_ID.value && !options.CACHE.value) {
                if (options.HEAPDLS.value) {
                    runHeapDL(options.HEAPDLS.value.collect { File f -> f.canonicalPath })
                }

                if (options.DONT_CACHE_FACTS.value)
                    log.info "Facts will not be cached."
                else {
                    log.info "Caching facts in $cacheDir"
                    deleteQuietly(cacheDir)
                    cacheDir.mkdirs()
                    FileOps.copyDirContentsWithRetry(factsDir, cacheDir)
                    new File(cacheDir, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
                }
            } else {
                log.warn "WARNING: Imported facts are not cached."
            }
            log.info "----"
        }

        if (options.SPECIAL_CONTEXT_SENSITIVITY_METHODS.value) {
            File origSpecialCSMethodsFile = new File(options.SPECIAL_CONTEXT_SENSITIVITY_METHODS.value.toString())
            File destSpecialCSMethodsFile = new File(factsDir, "SpecialContextSensitivityMethod.facts")
            Files.copy(origSpecialCSMethodsFile.toPath(), destSpecialCSMethodsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (options.X_ZIPPER.value) {
            File origZipperFile = new File(options.X_ZIPPER.value.toString())
            File destZipperFile = new File(factsDir, "ZipperPrecisionCriticalMethod.facts")
            Files.copy(origZipperFile.toPath(), destZipperFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (options.USER_DEFINED_PARTITIONS.value) {
            File origPartitionsFile = new File(options.USER_DEFINED_PARTITIONS.value.toString())
            File destPartitionsFile = new File(factsDir, "TypeToPartition.facts")
            Files.copy(origPartitionsFile.toPath(), destPartitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

    }


    private static List<String> flattenArgs(List<File> files, String opt) {
        return files.collect() { File f -> [opt, f.toString()] }.flatten() as List<String>
    }

    protected void runFrontEnd(Set<String> tmpDirs, FrontEnd frontEnd, CHA cha) {
        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        if (platform != "android" && platform != "java")
            throw new RuntimeException("Unsupported platform: ${platform}")

        def inputArgs = flattenArgs(inputFiles, '-i')
        def deps = flattenArgs(libraryFiles, '-ld')
        List<File> platforms = options.PLATFORMS.value as List<File>
        if (!platforms) {
            throw new RuntimeException("internal option '${options.PLATFORMS.name}' is empty")
        }

        if (!options.APP_REGEX.value) {
            throw new RuntimeException("Internal error: no application regex available for code fact generator.")
        }

        Collection<String> params = ["--application-regex", options.APP_REGEX.value.toString()]

        if (Logger.rootLogger.debugEnabled) {
            params.add("--debug")
        }

        if (platform == "android") {
            params.add("--android")
        }

        if (options.FACT_GEN_CORES.value) {
            params += ["--fact-gen-cores", options.FACT_GEN_CORES.value.toString()]
        }

        List<String> mainClasses = options.MAIN_CLASS.value as List<String>
        if (mainClasses != null && !mainClasses.empty) {
            options.MAIN_CLASS.value.each { String mainClass ->
                params += ["--main", mainClass]
            }
        }

        if (options.X_FACTS_SUBSET.value) {
            params += ["--facts-subset", options.X_FACTS_SUBSET.value.toString()]
        }

        if (options.EXTRACT_MORE_STRINGS.value) {
            params += ["--extract-more-strings"]
        }

        if (options.DRY_RUN.value) {
            params += ["--no-facts"]
        }

        if (options.X_R_OUT_DIR.value) {
            params += ["--R-out-dir", options.X_R_OUT_DIR.value.toString()]
        }

        if (options.DECODE_APK.value) {
            params += ["--decode-apk"]
        }

        if (options.X_DEX_FACT_GEN.value) {
            params += ["--dex"]
        }

        if (options.SCAN_NATIVE_CODE.value) {
            // The WALA/Dex front-ends currently do not record method strings; using this
            // functionality may introduce some imprecision.
            def check = { AnalysisOption opt ->
                if (opt.value)
                    println "WARNING: Option --${options.SCAN_NATIVE_CODE.name} is not fully compatible with --${opt.name}"
            }
            check(options.X_DEX_FACT_GEN)
            check(options.WALA_FACT_GEN)
            params += ["--scan-native-code"]
        }

        String nativeBackend = options.NATIVE_CODE_BACKEND.value
        if (nativeBackend == DoopAnalysisFamily.NATIVE_BACKEND_BUILTIN) {
            params += ["--native-backend-builtin"]
        } else if (nativeBackend == DoopAnalysisFamily.NATIVE_BACKEND_RADARE) {
            params += ["--native-backend-radare"]
        } else if (nativeBackend == DoopAnalysisFamily.NATIVE_BACKEND_BINUTILS) {
            params += ["--native-backend-binutils"]
        }

        if (options.ONLY_PRECISE_NATIVE_STRINGS.value) {
            params += ["--only-precise-native-strings"]
        }

        if (options.GENERATE_ARTIFACTS_MAP.value) {
            params += ["--write-artifacts-map"]
        }

        if (options.X_LEGACY_ANDROID_PROCESSING.value) {
            params += ["--legacy-android-processing"]
        }

        params.addAll(["--log-dir", Doop.doopLog])
        params.addAll(["-d", factsDir.toString()] + inputArgs)
        deps.addAll(platforms.collect { lib -> ["-l", lib.toString()] }.flatten() as Collection<String>)
        params.addAll(deps)

        if (frontEnd == FrontEnd.SOOT) {
            runSoot(platform, deps, platforms, params)
        } else if (frontEnd == FrontEnd.WALA) {
            runWala(platform, deps, platforms, params)
        } else if (frontEnd == FrontEnd.DEX) {
            runDexFactGen(platform, deps, platforms, params, tmpDirs, cha)
        } else {
            println("Unknown front-end: " + frontEnd)
        }
    }

    protected void runSoot(String platform, Collection<String> deps, List<File> platforms, Collection<String> params) {
        params += [ "--full" ]

        if (platform == "android") {
            // This uses all platformLibs.
            // params = ["--android-jars"] + platformLibs.collect({ f -> f.getAbsolutePath() })
            // This uses just platformLibs[0], assumed to be android.jar.
            params.addAll(["--android-jars", platforms.first().absolutePath])
        }

        if (options.SSA.value) {
            params += ["--ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params += ["--allow-phantom"]
        }

        if (options.REPORT_PHANTOMS.value) {
            params += ["--report-phantoms"]
        }

        if (options.X_IGNORE_WRONG_STATICNESS.value) {
            params += ["--ignore-wrong-staticness"]
        }

        if (options.GENERATE_JIMPLE.value) {
            params += ["--generate-jimple"]
        }

        if (options.X_IGNORE_FACTGEN_ERRORS.value) {
            params += ["--ignore-factgen-errors"]
        }

        if (options.ALSO_RESOLVE.value) {
            alsoResolve(params, options.ALSO_RESOLVE.value as Collection<String>)
        }

        File missingClasses = null
        if (options.THOROUGH_FACT_GEN.value) {
            missingClasses = File.createTempFile("fact-gen-missing-classes", ".tmp")
            missingClasses.deleteOnExit()
            params += ["--failOnMissingClasses", missingClasses.absolutePath ]
            // Restarting on fact generation error can only happen reliably in isolated mode.
            if (!options.X_ISOLATE_FACTGEN.value) {
                log.warn "WARNING: Option --${options.THOROUGH_FACT_GEN.name} turns on --${options.X_ISOLATE_FACTGEN.name}"
                options.X_ISOLATE_FACTGEN.value = true
            }
        }

        if (options.X_LOW_MEM.value) {
            params += ["--lowMem"]
        }

        log.debug "Params of soot: ${params.join(' ')}"

        factGenTime = Helper.timing {
            final int MAX_FACTGEN_RUNS = 3
            int factGenRun = 1
            boolean redo = true
            while (redo) {
                ClassLoader loader = null
                try {
                    redo = false
                    String SOOT_MAIN = "org.clyze.doop.soot.Main"
                    if (!JHelper.java9Plus() && options.X_LEGACY_SOOT_INVOCATION.value) {
                        // We invoke the Soot-based fact generator reflectively
                        // using a separate class-loader to be able to support
                        // multiple soot invocations in the same JVM
                        // (server-side). Note that information may be passed
                        // over the different class-loader border but this needs
                        // some careful code (a class "A" in the context of this
                        // class is not the same as "A" in the context of Soot);
                        // see exception handling below for details.
                        //
                        // TODO: Investigate whether this approach may lead to memory
                        // leaks, not only for soot but for all other Java-based tools,
                        // like jphantom.
                        //
                        // SETUP: to enable this mode, edit build.gradle as follows:
                        //
                        // (a) Add soot-fact-generator compile dependency:
                        //     dependencies {
                        //       ...
                        //       compile project(':generators:soot-fact-generator')
                        //       ...
                        //     }
                        //
                        // (b) For big inputs, give appropriate -Xss/-Xmx memory
                        //     parameters via Gradle option 'applicationDefaultJvmArgs'.
                        //
                        loader = ClassPathHelper.copyOfCurrentClasspath(log, this)
                        try {
                            String[] args = params.toArray(new String[params.size()])
                            Helper.execJavaNoCatch(loader, SOOT_MAIN, args)
                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException("Cannot find Soot-based front end.")
                        }
                    } else {
                        String[] jvmArgs = [ "-Dfile.encoding=UTF-8" ] as String[]
                        invokeFactGenerator('SOOT_FACT_GEN', Resource.SOOT_FACT_GENERATOR, jvmArgs, params, SOOT_MAIN)
                    }
                    // Check if fact generation must be restarted due to missing classes.
                    if (missingClasses != null && missingClasses.exists()) {
                        String[] extraClasses = missingClasses.readLines() as String[]
                        if (extraClasses.length > 0) {
                            // Retry with classes reported by the front-end.
                            if (factGenRun >= MAX_FACTGEN_RUNS)
                                System.err.println("Too many fact generation restarts, classes still not resolved: " + Arrays.toString(extraClasses))
                            else {
                                redo = true
                                factGenRun += 1
                                println("Restarting fact generation (run #${factGenRun}) with " + extraClasses.length + " more classes: " + Arrays.toString(extraClasses))
                                DoopAnalysis.alsoResolve(params, Arrays.asList(extraClasses))
                                missingClasses.delete()
                            }
                        }
                    }
                } catch (Throwable t) {
                    String msg = "Soot fact generation error (${t.class.name}): ${t.message}"
                    log.debug msg
                    if (isFatal(loader, t))
                        throw new RuntimeException("Fatal error, see log for details: ${t.toString()}")
                    if (factGenRun >= MAX_FACTGEN_RUNS) {
                        println "Too many fact generation restarts, aborting."
                        if (!(options.X_IGNORE_FACTGEN_ERRORS.value)) {
                            log.info "Errors occurred, maybe retry with --${options.X_IGNORE_FACTGEN_ERRORS.name}?"
                        }
                        throw new RuntimeException(msg)
                    } else {
                        redo = true
                        factGenRun += 1
                        println "Errors occurred, restarting fact generation (run #${factGenRun})."
                    }
                } finally {
                    // Restarting cannot add to current facts: non-deterministic names
                    // from different runs blow up relations (e.g., VarPointsTo).
                    if (redo) {
                        println "Reset facts directory: ${factsDir}"
                        initFactsDir()
                    }
                }
            }
        }
        log.info "Soot fact generation time: ${factGenTime}"
    }

    /** Reads the value of a field of a Throwable object thrown by a
     *  different class loader via an InvocationTargetException.
     *  Since Soot may run in a different class loader, we cannot do
     *  instanceof checks but have to resort to string checks to
     *  find the type of thrown exceptions.
     *
     * @param loader     the class loader
     * @param t          the throwable to check
     * @param className  the name of the (sub-)class of the Throwable
     * @param fieldName  the name of the field
     * @return           the value of the field, or null if the field
     *                   does not exist
     */
    Object getThrowableField(ClassLoader loader, Throwable t, String className, String fieldName) {
        if (t instanceof InvocationTargetException)
            t = ((InvocationTargetException) t).targetException as Throwable
        if (loader == null)
            loader = t.class.classLoader
        if (t.class.name == className) {
            Field classesFld = loader.loadClass(className).getDeclaredField(fieldName)
            classesFld.accessible = true
            return classesFld.get(t)
        }
        return null
    }

    /**
     * Read the 'fatal' field of a DoopErrorCodeException.
     *
     * @param loader  the class loader
     * @param t       the throwable to check
     * @return        the value of the field or false if the field does not exist
     */
    boolean isFatal(ClassLoader loader, Throwable t) {
        Boolean b = getThrowableField(loader, t, 'org.clyze.doop.common.DoopErrorCodeException', 'fatal')
        return b ? b as boolean : false
    }

    private static void alsoResolve(Collection<String> params, Collection<String> extraClasses) {
        params.addAll(extraClasses.collect { c -> ["--also-resolve", c] }.flatten() as Collection<String>)
    }

    protected void runWala(String platform, Collection<String> deps, List<File> platforms, Collection<String> params) {
        if ((platform != "android") && (platform != "java")) {
            throw new RuntimeException("Unsupported platform: ${platform}")
        }

        log.debug "Params of wala: ${params.join(' ')}"

        try {
            factGenTime = Helper.timing {
                invokeFactGenerator('WALA_FACT_GEN', Resource.WALA_FACT_GENERATOR, null, params, 'org.clyze.doop.wala.Main')
            }
        } catch(walaError){
            walaError.printStackTrace()
            throw new RuntimeException("Wala fact generation Error: $walaError", walaError)
        }
        log.info "Wala fact generation time: ${factGenTime}"
    }

    protected void runDexFactGen(String platform, Collection<String> deps, List<File> platforms, Collection<String> params, Set<String> tmpDirs, CHA cha) {

        // params += [ "--print-phantoms" ]

        log.debug "Params of dex front-end: ${params.join(' ')}"

        try {
            invokeFactGenerator('DEX_FACT_GEN', Resource.DEX_FACT_GENERATOR, null, params, 'org.clyze.doop.dex.DexInvoker')
        } catch (Exception ex) {
            ex.printStackTrace()
        }
    }

    protected void runPython(Set<String> tmpDirs) {
        Collection<String> params = []
        Collection<String> depArgs = []
        def inputArgs = flattenArgs(inputFiles, '-i')
        def deps = flattenArgs(libraryFiles, '-ld')

        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        assert platform == "python"

        params += ["--python"]

        if (options.FACT_GEN_CORES.value) {
            params += ["--fact-gen-cores", options.FACT_GEN_CORES.value.toString()]
        }
        if (options.GENERATE_JIMPLE.value) {
            params += ["--generate-ir"]
        }
        if (options.SINGLE_FILE_ANALYSIS.value) {
            params += ["--single-file-analysis"]
        }
        //depArgs = (platformLibs.collect{ lib -> ["-l", lib.toString()] }.flatten() as Collection<String>) + deps
        params = params + inputArgs + depArgs + ["-d", factsDir.toString()]

        log.debug "Params of wala (Python mode): ${params.join(' ')}"

        try {
            factGenTime = Helper.timing {
                invokeFactGenerator('PYTHON_FACT_GEN', Resource.WALA_FACT_GENERATOR, null, params, 'org.clyze.doop.wala.Main')
            }
        } catch(walaError){
            walaError.printStackTrace()
            throw new RuntimeException("Wala fact generation Error: $walaError", walaError)
        }
        log.info "Wala fact generation time: ${factGenTime}"
    }

    protected void runJPhantom() {
        log.info "-- Running jphantom to generate complement JAR --"

        String jar = inputFiles[0].toString()
        String jarName = FilenameUtils.getBaseName(jar)
        String jarExt = FilenameUtils.getExtension(jar)
        if (!['jar', 'zip'].contains(jarExt.toLowerCase())) {
            log.error "ERROR: jphantom does not support ${jarExt} inputs"
            throw DoopErrorCodeException.error23()
        }
        if (inputFiles.size() > 1)
            log.warn "WARNING: jphantom will only run on first input JAR."
        String newJar = "${jarName}-complemented.${jarExt}"
        String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
        log.debug "Params of jphantom: ${params.join(' ')}"

        // We invoke the main method reflectively to avoid adding jphantom as a compile-time dependency.
        ClassLoader loader = ClassPathHelper.copyOfCurrentClasspath(log, this)
        Helper.execJavaNoCatch(loader, "org.clyze.jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        inputFiles[0] = FileOps.findFileOrThrow("$outDir/$newJar", "jphantom invocation failed")
    }

    // Generate the text of the "meta" file of the cached facts directory.
    protected String cacheMeta() {
        Collection<String> inputs = DoopAnalysisFamily.getAllInputs(options)
            .collect { it.toString() }
        Collection<String> cacheOptions = options.values().findAll { AnalysisOption<?> opt ->
            opt.forCacheID
        }.collect {
            AnalysisOption<?> opt -> opt.toString()
        }.sort() as List<String>
        return (inputs + cacheOptions).join("\n")
    }

    protected void runHeapDL(List<String> filenames) {

        log.info "Running HeapDL..."

        // Support compressed formats: decompress
        List<String> tmpFiles = []
        List<String> processed = filenames.collect { String heapdl ->
            if (heapdl.toLowerCase().endsWith(".gz")) {
                Path tmpPath = Files.createTempFile("gzip-", ".hprof")
                tmpPath.toFile().deleteOnExit()
                String tmpPathName = tmpPath.toString()
                log.debug "Decompressing ${heapdl} to ${tmpPathName}..."
                FileOps.decompressGzipFile(heapdl, tmpPathName)
                tmpFiles << tmpPathName
                return tmpPathName
            } else {
                return heapdl
            }
        }
        List<String> hprofs = [] as List
        List<String> traces = [] as List
        processed.each {
            if (it.endsWith(".hprof")) {
                log.info "Heap snapshot input: ${it}"
                hprofs << it
            } else {
                log.info "Stack trace input: ${it}"
                traces << it
            }
        }

        try {
            MemoryAnalyser memoryAnalyser = new MemoryAnalyser(hprofs, traces, options.HEAPDL_NOSTRINGS.value ? false : true)
            int n = memoryAnalyser.outputToDir(factsDir, "2ObjH")
            log.info("Generated " + n + " additional facts from memory dump")
        } catch (Exception e) {
            e.printStackTrace()
        }

        // Delete any temporary files created.
        tmpFiles.each { deleteQuietly(new File(it)) }
    }

    protected final void handleImportDynamicFacts() {
        if (options.IMPORT_DYNAMIC_FACTS.value) {
            File f = new File(options.IMPORT_DYNAMIC_FACTS.value.toString())
            if (f.exists()) {
                throw new RuntimeException("Facts file ${f.canonicalPath} already exists, cannot overwrite it with imported file of same name.")
            } else {
                copyFileToDirectory(f, factsDir)
            }
        }
    }

    /**
     * Invokes a fact generator, either in isolated mode (invoked as external Java
     * process) or via reflection. (Choice controlled by X_ISOLATE_FACTGEN option
     * and availability of the fact generator as a library.)
     *
     * @param TAG        the tag to use to mark output (if external process is used)
     * @param jvmArgs    the JVM arguments to use (memory options should be set separately, via properties)
     * @param generator  the generator when bundled as an external program
     * @param params     the fact generator parameters
     * @param mainClass  the main class of the fact generator
     */
    void invokeFactGenerator(String TAG, Resource generator, String[] jvmArgs,
                             Collection<String> params, String mainClass) {
        // Detect if generator main is available.
        def main = null
        try {
            main = Class.forName(mainClass).getDeclaredMethod("main", String[].class)
        } catch(all) {
            if (!options.X_ISOLATE_FACTGEN.value) {
                log.debug "Fact generator main class is not available, rebuild Doop with '${generator}' linked: ${all.message}"
            }
        }

        // Write arguments to file and pass that to the
        // generator. This fixes too-long argument lists.
        Path tmpParams = Files.createTempFile("params-", "")
        tmpParams.toFile().deleteOnExit()
        String argsFile = tmpParams.toString()
        // Some special options should not be passed via the file.
        List<String> specialOpts = ["--python"]
        List<String> args = []
        (new File(argsFile)).withWriterAppend { w -> params.each { String opt ->
            if (specialOpts.contains(opt)) {
                args.add(opt)
            } else {
                w.writeLine(opt)
            }
        } }
        args.addAll([ "--args-file", argsFile ])
        String[] args0 = args as String[]

        if ((!main) || options.X_ISOLATE_FACTGEN.value) {
            invokeExtFactGenerator(TAG, jvmArgs, generator, args0)
        } else {
            try {
                main.invoke(null, [args0] as Object[])
            } catch (ex) {
                ex.printStackTrace()
                throw new RuntimeException("Could not invoke '${generator}' as a linked library, try --${options.X_ISOLATE_FACTGEN.name}: ${ex.message}")
            }
        }
    }

    /**
     * Invoke a fact generator bundled as a JAR in Doop's resources.
     *
     * @param TAG          the tag to use to mark fact generator output
     * @param jvmArgs      the JVM arguments to use (memory options should be set separately, via properties)
     * @param resource     the fact generator JAR
     * @param args         the fact generation arguments
     */
    void invokeExtFactGenerator(String TAG, String[] jvmArgs, Resource resource, String[] args) {
        if (jvmArgs == null)
            jvmArgs = new String[0]

        // Read properties to get JVM arguments for calling the fact generators.
        List<String> jvmMemArgs = []

        String maxHeapSize = System.getProperty("maxHeapSize")
        if (maxHeapSize != null)
            jvmMemArgs.add("-Xmx" + maxHeapSize)

        String stackSize = System.getProperty("stackSize")
        if (stackSize != null)
            jvmMemArgs.add("-Xss" + stackSize)

        String reservedCodeCacheSize = System.getProperty("reservedCodeCacheSize")
        if (reservedCodeCacheSize)
            jvmMemArgs.add("-XX:ReservedCodeCacheSize=" + reservedCodeCacheSize)

        log.debug "Memory JVM args: ${jvmMemArgs}"
        String[] jvmArgs0 = (jvmArgs + jvmMemArgs) as String[]
        Resource.invokeResourceJar(DoopAnalysis.class, log, TAG, jvmArgs0, resource, args)
    }
}
