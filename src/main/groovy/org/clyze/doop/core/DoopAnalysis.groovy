package org.clyze.doop.core

import groovy.transform.TypeChecked
import groovy.util.logging.Log4j
import heapdl.core.MemoryAnalyser
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.Analysis
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.common.CHA
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.common.KeepSpecProcessor
import org.clyze.doop.common.FrontEnd
import org.clyze.doop.dex.DexInvoker
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.python.PythonInvoker
import org.clyze.doop.util.ClassPathHelper
import org.clyze.doop.wala.WalaInvoker
import org.clyze.utils.*
import org.codehaus.groovy.runtime.StackTraceUtils

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.apache.commons.io.FileUtils.*

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 */
@Log4j
@TypeChecked
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
     * Used for invoking external commnands
     */
    protected Executor executor

    /**
     * Used for invoking the C preprocessor
     */
    protected CPreprocessor cpp

    /**
     * Total time for the soot invocation
     */
    protected long factGenTime

    /**
     * The suffix of information flow platforms.
     */
    static final INFORMATION_FLOW_SUFFIX = "-sources-and-sinks"

    String getId() { options.USER_SUPPLIED_ID.value as String }

    String getName() { options.ANALYSIS.value.toString().replace(File.separator, "-") }

    File getOutDir() { options.OUT_DIR.value as File }

    File getCacheDir() { options.CACHE_DIR.value as File }

    List<File> getInputFiles() { options.INPUTS.value as List<File> }

    List<File> getLibraryFiles() { options.LIBRARIES.value as List<File> }

    FactGenerator0 gen0

    /*
     * Use a java-way to construct the instance (instead of using Groovy's automatically generated Map constructor)
     * in order to ensure that internal state is initialized at one point and the init method is no longer required.
     */

    protected DoopAnalysis(Map<String, AnalysisOption> options,
                           InputResolutionContext ctx,
                           Map<String, String> commandsEnvironment) {
        super(DoopAnalysisFamily.instance, options)
        this.ctx = ctx

        if (!options.X_START_AFTER_FACTS.value) {
            log.info "New $name analysis"
            log.info "Id       : $id"
            log.info "Inputs   : ${inputFiles.join(', ')}"
            log.info "Libraries: ${libraryFiles.join(', ')}"
        } else
            log.info "New $name analysis on user-provided facts at ${options.X_START_AFTER_FACTS.value} - id: $id"

        if (options.X_STOP_AT_FACTS.value)
            factsDir = new File(options.X_STOP_AT_FACTS.value.toString())
        else
            factsDir = new File(outDir, "facts")

        gen0 = new FactGenerator0(factsDir)

        database = new File(outDir, "database")

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
        if (options.X_SYMLINK_CACHED_FACTS.value) {
            try {
                Path fromDirPath = FileSystems.default.getPath(fromDir.canonicalPath)
                Files.createSymbolicLink(factsDir.toPath(), fromDirPath)
                return
            } catch (UnsupportedOperationException ignored) {
                System.err.println("Filesystem does not support symbolic links, copying directory instead...")
            }
        }

        factsDir.mkdirs()
        log.debug "Copying: ${fromDir} -> ${factsDir}"
        FileOps.copyDirContents(fromDir, factsDir)
    }

    /**
     * Reuses an existing facts directory. May add more facts on top of
     * the existing facts, if appropriate command line options are set.
     *
     * @param fromDir the existing directory containing the facts
     */
    protected void reuseFacts(File fromDir) {
        linkOrCopyFacts(fromDir)
        def keepSpec = options.KEEP_SPEC.value as String
        if (keepSpec) {
            KeepSpecProcessor.processDir(factsDir, keepSpec)
        }
        gen0.writeMainClassFacts(options.MAIN_CLASS.value)
    }

    protected void generateFacts() throws DoopErrorCodeException {
        deleteQuietly(factsDir)

        if (cacheDir.exists() && options.CACHE.value) {
            log.info "Using cached facts from $cacheDir"
            reuseFacts(cacheDir)
        } else if (cacheDir.exists() && options.X_START_AFTER_FACTS.value) {
            def importedFactsDir = options.X_START_AFTER_FACTS.value as String
            log.info "Using user-provided facts from ${importedFactsDir} in ${factsDir}"
            reuseFacts(new File(importedFactsDir))
        } else {
            factsDir.mkdirs()
            log.info "-- Fact Generation --"

            if (options.RUN_JPHANTOM.value) {
                runJPhantom()
            }

            if (options.INFORMATION_FLOW_EXTRA_CONTROLS.value) {
                gen0.writeExtraSensitiveControls(options.INFORMATION_FLOW_EXTRA_CONTROLS.value.toString())
            }

            def existingFactsDir = options.X_EXTEND_FACTS.value as File
            if (existingFactsDir) {
                log.info "Expanding upon facts found in: $existingFactsDir.canonicalPath"
                linkOrCopyFacts(existingFactsDir)
            }

            Set<String> tmpDirs = [] as Set
            try {
                if (options.PYTHON.value) {
                    runPython(tmpDirs)
                } else if (options.WALA_FACT_GEN.value) {
                    runFrontEnd(tmpDirs, FrontEnd.WALA, null)
                } else if (options.DEX_FACT_GEN.value) {
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
                throw new DoopErrorCodeException(8, all)
            } finally {
                JHelper.cleanUp(tmpDirs)
            }

            if (options.X_UNIQUE_FACTS.value) {
                def timing = Helper.timing {
                    factsDir.eachFileMatch(~/.*.facts/) { file ->
                        def uniqueLines = file.readLines().toSet()
                        def tmp = new File(factsDir, "${file.name}.tmp")
                        tmp.withWriter { w -> uniqueLines.each { w.writeLine(it) } }
                        tmp.renameTo(file)
                    }
                }
                log.info "Time to make facts unique: $timing"
            }

            touch(new File(factsDir, "MainClass.facts"))

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

            if (!options.X_START_AFTER_FACTS.value) {
                if (options.HEAPDLS.value && !options.X_DRY_RUN.value) {
                    runHeapDL(options.HEAPDLS.value.collect { File f -> f.canonicalPath })
                }

                log.info "Caching facts in $cacheDir"
                deleteQuietly(cacheDir)
                cacheDir.mkdirs()
                FileOps.copyDirContentsWithRetry(factsDir, cacheDir)
                new File(cacheDir, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
            } else {
                log.warn "WARNING: Imported facts are not cached."
            }
            log.info "----"
        }

        gen0.writeMainClassFacts(options.MAIN_CLASS.value)

        if (options.SPECIAL_CONTEXT_SENSITIVITY_METHODS.value) {
            File origSpecialCSMethodsFile = new File(options.SPECIAL_CONTEXT_SENSITIVITY_METHODS.value.toString())
            File destSpecialCSMethodsFile = new File(factsDir, "SpecialContextSensitivityMethod.facts")
            Files.copy(origSpecialCSMethodsFile.toPath(), destSpecialCSMethodsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (options.ZIPPER.value) {
            File origZipperFile = new File(options.ZIPPER.value.toString())
            File destZipperFile = new File(factsDir, "ZipperPrecisionCriticalMethod.facts")
            Files.copy(origZipperFile.toPath(), destZipperFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (options.USER_DEFINED_PARTITIONS.value) {
            File origPartitionsFile = new File(options.USER_DEFINED_PARTITIONS.value.toString())
            File destPartitionsFile = new File(factsDir, "TypeToPartition.facts")
            Files.copy(origPartitionsFile.toPath(), destPartitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

    }

    private List<String> getInputArgsJars(Set<String> tmpDirs) {
        def inputArgs = inputFiles.collect() { File f -> ["-i", f.toString()] }.flatten() as Collection<String>
        return AARUtils.toJars(inputArgs as List<String>, false, tmpDirs)
    }

    private List<String> getDepsJars(Set<String> tmpDirs) {
        def deps = libraryFiles.collect { File f -> ["-ld", f.toString()] }.flatten() as Collection<String>
        return AARUtils.toJars(deps as List<String>, false, tmpDirs)
    }

    protected void runFrontEnd(Set<String> tmpDirs, FrontEnd frontEnd, CHA cha) {
        if (options.SKIP_CODE_FACTGEN.value) {
            log.info "Skipping facts generation for code inputs."
            return
        }

        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        if (platform != "android" && platform != "java")
            throw new RuntimeException("Unsupported platform: ${platform}")

        def inputArgs = getInputArgsJars(tmpDirs)
        def deps = getDepsJars(tmpDirs)
        List<File> platforms = options.PLATFORMS.value as List<File>
        if (!platforms) {
            throw new RuntimeException("internal option '${options.PLATFORMS.name}' is empty")
        }

        Collection<String> params = ["--application-regex", options.APP_REGEX.value.toString()]

        if (platform == "android") {
            params.add("--android")
        }

        if (options.FACT_GEN_CORES.value) {
            params += ["--fact-gen-cores", options.FACT_GEN_CORES.value.toString()]
        }

        if (options.X_FACTS_SUBSET.value) {
            params += ["--facts-subset", options.X_FACTS_SUBSET.value.toString()]
        }

        if (options.KEEP_SPEC.value) {
            params += ["--keep-spec", options.KEEP_SPEC.value.toString()]
        }

        if (options.EXTRACT_MORE_STRINGS.value) {
            params += ["--extract-more-strings"]
        }

        if (options.X_DRY_RUN.value) {
            params += ["--no-facts"]
        }

        if (options.X_R_OUT_DIR.value) {
            params += ["--R-out-dir", options.X_R_OUT_DIR.value.toString()]
        }

        if (options.DECODE_APK.value) {
            params += ["--decode-apk"]
        }

        if (options.DEX_FACT_GEN.value) {
            params += ["--dex"]
        }

        if (options.SCAN_NATIVE_CODE.value) {
            params += ["--scan-native-code"]
        }

        if (options.GENERATE_ARTIFACTS_MAP.value) {
            params += ["--write-artifacts-map"]
        }

        if (options.LEGACY_ANDROID_PROCESSING.value) {
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

        if (options.DONT_REPORT_PHANTOMS.value) {
            params += ["--dont-report-phantoms"]
        }

        if (options.RUN_FLOWDROID.value) {
            params += ["--run-flowdroid"]
        }

        if (options.X_IGNORE_WRONG_STATICNESS.value) {
            params += ["--ignoreWrongStaticness"]
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

        if (options.THOROUGH_FACT_GEN.value) {
            if (java9Plus()) {
                log.warn "WARNING: Option not supported in this Java version and will be ignored: --${options.THOROUGH_FACT_GEN.name}"
            } else {
                params += ["--failOnMissingClasses"]
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
                // We invoke Soot reflectively using a separate class-loader to
                // be able to support multiple soot invocations in the same JVM
                // (server-side). Note that information may be passed over the
                // different class-loader border but this needs some careful
                // code (a class "A" in the context of this class is not the
                // same as "A" in the context of Soot); see exception handling
                // below for details.
                //
                // TODO: Investigate whether this approach may lead to memory
                // leaks, not only for soot but for all other Java-based tools,
                // like jphantom.  In such a case, we should invoke all
                // Java-based tools using a separate process.
                ClassLoader loader = ClassPathHelper.copyOfCurrentClasspath(log, this)
                try {
                    redo = false
                    String SOOT_MAIN = "org.clyze.doop.soot.Main"
                    def args = params.toArray(new String[params.size()])
                    if (!java9Plus()) {
                        Helper.execJavaNoCatch(loader, SOOT_MAIN, args)
                    } else {
                        log.warn "WARNING: Calling Soot as external process, this may use more memory."
                        String classpath = System.getenv("DOOP_EXT_CLASSPATH")
                        if (classpath == null) {
                            throw new RuntimeException("Missing classpath environment variable DOOP_EXT_CLASSPATH")
                        }
                        JHelper.runClass(classpath.split(":"), SOOT_MAIN, args, "SOOT_FACT_GEN", true);
                    }
                } catch (Throwable t) {
                    if (isFatal(loader, t)) {
                        throw new RuntimeException("Fatal error, see log for details.")
                    }
                    // Try to restart fact generation a limited number of times
                    // (e.g., if Soot randomly fails or classes are missing).
                    String[] extraClasses = checkMissingClasses(loader, t)
                    if (extraClasses != null) {
                        // Retry with classes reported by the front-end.
                        if (factGenRun >= MAX_FACTGEN_RUNS) {
                            System.err.println("Too many fact generation restarts, classes still not resolved: " + Arrays.toString(extraClasses))
                        } else {
                            redo = true
                            factGenRun += 1
                            println("Restarting fact generation (run #${factGenRun}) with " + extraClasses.length + " more classes: " + Arrays.toString(extraClasses))
                            DoopAnalysis.alsoResolve(params, Arrays.asList(extraClasses))
                        }
                    } else if (factGenRun >= MAX_FACTGEN_RUNS) {
                        println "Too many fact generation restarts, aborting."
                        if (!(options.X_IGNORE_FACTGEN_ERRORS.value)) {
                            log.info "Errors occurred, maybe retry with --${options.X_IGNORE_FACTGEN_ERRORS.name}?"
                        }
                        System.err.println(t.message)
                        throw new RuntimeException("Soot fact generation error")
                    } else {
                        redo = true
                        factGenRun += 1
                        println "Errors occurred, restarting fact generation (run #${factGenRun})."
                    }
                    // We cannot add to current facts: non-deterministic names
                    // from different runs blow up relations (e.g., VarPointsTo).
                    if (redo) {
                        println "Deleting ${factsDir}..."
                        deleteQuietly(factsDir)
                    }
                }
            }
        }
        log.info "Soot fact generation time: ${factGenTime}"
    }

    /** Reads the value of a field of a Throwable object thrown by a
     *  different class loader via an InvocationTargetException.
     *  Since Soot runs in a different class loader, we cannot do
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
        if (t instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) t).targetException as Throwable
            if (cause.getClass().getName() == className) {
                Field classesFld = loader.loadClass(className).getDeclaredField(fieldName)
                classesFld.setAccessible(true)
                return classesFld.get(cause)
            }
        }
        return null
    }


    /**
     * Read the 'classes' field of MissingClassesException.
     *
     * @param loader  the class loader
     * @param t       the throwable to check
     *  @return       if t is a MissingClassesException, field
     *                'classes' is returned here, otherwise null.
     */
    String[] checkMissingClasses(ClassLoader loader, Throwable t) {
        return getThrowableField(loader, t, 'org.clyze.doop.soot.MissingClassesException', 'classes') as String[]
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
        if (platform == "android") {
            // This uses all platformLibs.
            // params = ["--full"] + depArgs + ["--android-jars"] + platformLibs.collect({ f -> f.getAbsolutePath() })
            // This uses just platformLibs[0], assumed to be android.jar.
            params.addAll(["--android-jars", platforms.first().absolutePath])
        } else if (platform != "java") {
            throw new RuntimeException("Unsupported platform: ${platform}")
        }

        log.debug "Params of wala: ${params.join(' ')}"

        try {
            factGenTime = Helper.timing {
                //We invoke soot reflectively using a separate class-loader to be able
                //to support multiple soot invocations in the same JVM @ server-side.
                //TODO: Investigate whether this approach may lead to memory leaks,
                //not only for soot but for all other Java-based tools, like jphantom.
                //In such a case, we should invoke all Java-based tools using a
                //separate process.
                WalaInvoker wala = new WalaInvoker()
                wala.main(params.toArray(new String[params.size()]))
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
            DexInvoker.start(params.toArray(new String[params.size()]), cha)
        } catch (Exception ex) {
            ex.printStackTrace()
        }
    }

    protected void runPython(Set<String> tmpDirs) {
        Collection<String> params = []
        Collection<String> depArgs = []
        def inputArgs = getInputArgsJars(tmpDirs)
        def deps = getDepsJars(tmpDirs)

        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        assert platform == "python"

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

        log.debug "Params of wala: ${params.join(' ')}"

        try {
            factGenTime = Helper.timing {
                PythonInvoker wala = new PythonInvoker()
                wala.main(params.toArray(new String[params.size()]))
            }
        } catch(walaError){
            walaError.printStackTrace()
            throw new RuntimeException("Wala fact generation Error: $walaError", walaError)
        }
        log.info "Wala fact generation time: ${factGenTime}"
    }

    protected void runJPhantom() {
        log.info "-- Running jphantom to generate complement jar --"

        String jar = inputFiles[0].toString()
        String jarName = FilenameUtils.getBaseName(jar)
        String jarExt = FilenameUtils.getExtension(jar)
        if (jarExt.toLowerCase() == 'apk') {
            log.info "Error: jphantom does not support .apk inputs"
            throw new DoopErrorCodeException(23)
        }
        String newJar = "${jarName}-complemented.${jarExt}"
        String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
        log.debug "Params of jphantom: ${params.join(' ')}"

        // We invoke the main method reflectively to avoid adding jphantom as a compile-time dependency.
        ClassLoader loader = ClassPathHelper.copyOfCurrentClasspath(log, this)
        Helper.execJava(loader, "org.clyze.jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        inputFiles[0] = FileOps.findFileOrThrow("$outDir/$newJar", "jphantom invocation failed")
    }

    // Generate the text of the "meta" file of the cached facts directory.
    protected String cacheMeta() {
        Collection<String> inputs = DoopAnalysisFamily.getAllInputs(options)
            .collect { it.toString() }
        Collection<String> cacheOptions = options.values().findAll {
            it.forCacheID
        }.collect {
            AnalysisOption option -> option.toString()
        }.sort()
        return (inputs + cacheOptions).join("\n")
    }

    protected void runHeapDL(List<String> filenames) {

        log.info "Running HeapDL..."

        // Support compressed formats: decompress
        List<String> tmpFiles = []
        List<String> processed = filenames.collect { String heapdl ->
            if (heapdl.toLowerCase().endsWith(".gz")) {
                String tmpPath = Files.createTempFile("gzip-", ".hprof").toString()
                log.debug "Decompressing ${heapdl} to ${tmpPath}..."
                FileOps.decompressGzipFile(heapdl, tmpPath)
                tmpFiles << tmpPath
                return tmpPath
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
            int n = memoryAnalyser.getAndOutputFactsToDB(factsDir, "2ObjH")
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
     * Checks if the Java runtime is 9+.
     *
     * @return true if the runtime supports Java 9+
     */
    public static boolean java9Plus() {
        try {
            Class c = Class.forName('java.lang.Runtime$Version')
            return true
        } catch (ClassNotFoundException ex) {
            return false
        }
    }
}
