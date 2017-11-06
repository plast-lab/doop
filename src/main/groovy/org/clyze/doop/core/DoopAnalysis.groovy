package org.clyze.doop.core

import groovy.transform.TypeChecked
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.Analysis
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.datalog.LBWorkspaceConnector
import heapdl.core.MemoryAnalyser
import org.clyze.doop.input.InputResolutionContext
import org.clyze.utils.CPreprocessor
import org.clyze.utils.Executor
import org.clyze.utils.FileOps
import org.clyze.utils.Helper

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.touch

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 */
@TypeChecked
abstract class DoopAnalysis extends Analysis implements Runnable {

    /**
     * Used for logging various messages
     */
    protected Log logger

    /**
     * The facts dir for the input facts
     */
    protected File factsDir

    /**
     * The cache dir for the input facts
     */

    protected File cacheDir

    /**
     * The underlying workspace
     */
    protected File database

    /**
     * The dir used for running averroes
     */
    protected File averroesDir

    /**
     * The analysis input resolution mechanism
     */
    InputResolutionContext ctx

    /**
     * The jre library jars for soot
     */
    protected List<File> platformLibs

    /**
     * Used for invoking external commnands
     */
    protected Executor executor

    /**
     * Used for invoking the C preprocessor
     */
    protected CPreprocessor cpp

    /**
     * Interface with the underlying workspace
     */
    LBWorkspaceConnector connector

    /**
     * Total time for the soot invocation
     */
    protected long sootTime

    /*
     * Use a java-way to construct the instance (instead of using Groovy's automatically generated Map constructor)
     * in order to ensure that internal state is initialized at one point and the init method is no longer required.
     */
    protected DoopAnalysis(String id,
                           String name,
                           Map<String, AnalysisOption> options,
                           InputResolutionContext ctx,
                           File outDir,
                           File cacheDir,
                           List<File> inputFiles,
                           List<File> platformLibs,
                           Map<String, String> commandsEnvironment) {
        super(DoopAnalysisFamily.instance, id, name, options, outDir, inputFiles)
        this.ctx = ctx
        this.cacheDir = cacheDir
        this.platformLibs = platformLibs

        logger      = LogFactory.getLog(getClass())

        factsDir    = new File(outDir, "facts")
        database    = new File(outDir, "database")
        averroesDir = new File(outDir, "averroes")

        executor    = new Executor(commandsEnvironment)
        cpp         = new CPreprocessor(this, executor)
    }

    String toString() {
        return [id:id, name:name, outDir:outDir, cacheDir:cacheDir, inputFiles:ctx.toString()]
                .collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") + "\n" +
                options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    @Override
    abstract void run()

    protected void generateFacts() {
        deleteQuietly(factsDir)
        factsDir.mkdirs()

        if (cacheDir.exists() && options.CACHE.value) {
            logger.info "Using cached facts from $cacheDir"
            FileOps.copyDirContents(cacheDir, factsDir)
        }
        else if (cacheDir.exists() && options.X_START_AFTER_FACTS.value) {
            logger.info "Using user-provided facts from $factsDir"
            FileOps.copyDirContents(cacheDir, factsDir)
        }
        else {
            logger.info "-- Fact Generation --"

            if (options.RUN_JPHANTOM.value) {
                runJPhantom()
            }

            if (options.RUN_AVERROES.value) {
                runAverroes()
            }

            runSoot()

            touch(new File(factsDir, "ApplicationClass.facts"))
            touch(new File(factsDir, "Properties.facts"))

            def benchmark = FilenameUtils.getBaseName(inputFiles[0].toString())
            def benchmarkCap = (benchmark as String).toLowerCase().capitalize()

            if (options.DACAPO.value) {
                new File(factsDir, "Dacapo.facts").withWriter { w ->
                    w << "dacapo.${benchmark}.${benchmarkCap}Harness" + "\t" + "<dacapo.parser.Config: void setClass(java.lang.String)>"
                }
            }
            else if (options.DACAPO_BACH.value) {
                new File(factsDir, "Dacapo.facts").withWriter { w ->
                    w << "org.dacapo.harness.${benchmarkCap}" + "\t" + "<org.dacapo.parser.Config: void setClass(java.lang.String)>"
                }
            }
            else {
                touch(new File(factsDir, "Dacapo.facts"))
            }
            if (options.TAMIFLEX.value) {
                File origTamFile = new File(options.TAMIFLEX.value.toString())

                new File(factsDir, "Tamiflex.facts").withWriter { w ->
                    origTamFile.eachLine { line ->
                        w << line
                                .replaceFirst(/;[^;]*;$/, "")
                                .replaceFirst(/;$/, ";0")
                                .replaceFirst(/(^.*;.*)\.([^.]+;[0-9]+$)/) { full, first, second -> first + ";" + second+ "\n" }
                                .replaceAll(";", "\t").replaceFirst(/\./, "\t")
                    }
                }
            }

            if (options.HEAPDL.value && !options.X_DRY_RUN.value) {
                runHeapDL(options.HEAPDL.value.toString())
            }

            logger.info "Caching facts in $cacheDir"
            deleteQuietly(cacheDir)
            cacheDir.mkdirs()
            FileOps.copyDirContents(factsDir, cacheDir)
            new File(cacheDir, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
            logger.info "----"
        }
    }

    abstract protected void initDatabase()

    abstract protected void basicAnalysis()

    abstract protected void mainAnalysis()

    abstract protected void produceStats()

    abstract protected void runTransformInput()

    protected void runSoot() {
        Collection<String> depArgs

        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        assert platform == "android" || platform == "java"

        if (options.RUN_AVERROES.value) {
            //change linked arg and injar accordingly
            inputFiles[0] = FileOps.findFileOrThrow("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar".toString()]
        }
        else {
            def deps = inputFiles.drop(1).collect{ File f -> ["-l", f.toString()]}.flatten() as Collection<String>
            depArgs = (platformLibs.collect{ lib -> ["-l", lib.toString()] }.flatten() as Collection<String>) + deps
        }

        Collection<String> params

        switch(platform) {
            case "java":
                params = ["--full"] + depArgs + ["--application-regex", options.APP_REGEX.value.toString()]
                break
            case "android":
                // This uses all platformLibs.
                // params = ["--full"] + depArgs + ["--android-jars"] + platformLibs.collect({ f -> f.getAbsolutePath() })
                // This uses just platformLibs[0], assumed to be android.jar.
                params = ["--full"] + depArgs + ["--android-jars"] + [platformLibs[0].getAbsolutePath()]
        break
            default:
                throw new RuntimeException("Unsupported platform")
        }

        if (options.SSA.value) {
            params += ["--ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params += ["--allow-phantom"]
        }

        if (options.RUN_FLOWDROID.value) {
            params += ["--run-flowdroid"]
        }

        if (options.ONLY_APPLICATION_CLASSES_FACT_GEN.value) {
            params += ["--only-application-classes-fact-gen"]
        }

        if (options.GENERATE_JIMPLE.value) {
            params += ["--generate-jimple"]
        }

        if (options.X_DRY_RUN.value) {
            params += ["--noFacts"]
        }

        if (options.UNIQUE_FACTS.value) {
            params += ["--uniqueFacts"]
        }

        if (options.X_R_OUT_DIR.value) {
            params += ["--R-out-dir", options.X_R_OUT_DIR.value.toString()]
        }

        params = params + ["-d", factsDir.toString(), inputFiles[0].toString()]

        logger.debug "Params of soot: ${params.join(' ')}"

        sootTime = Helper.timing {
            //We invoke soot reflectively using a separate class-loader to be able
            //to support multiple soot invocations in the same JVM @ server-side.
            //TODO: Investigate whether this approach may lead to memory leaks,
            //not only for soot but for all other Java-based tools, like jphantom
            //or averroes.
            //In such a case, we should invoke all Java-based tools using a
            //separate process.
            ClassLoader loader = sootClassLoader()
            Helper.execJava(loader, "org.clyze.doop.soot.Main", params.toArray(new String[params.size()]))
        }

        logger.info "Fact generation time: ${sootTime}"
    }

    protected void runJPhantom(){
        logger.info "-- Running jphantom to generate complement jar --"

        String jar = inputFiles[0].toString()
        String jarName = FilenameUtils.getBaseName(jar)
        String jarExt = FilenameUtils.getExtension(jar)
        String newJar = "${jarName}-complemented.${jarExt}"
        String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
        logger.debug "Params of jphantom: ${params.join(' ')}"

        //we invoke the main method reflectively to avoid adding jphantom as a compile-time dependency
        ClassLoader loader = phantomClassLoader()
        Helper.execJava(loader, "org.clyze.jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        inputFiles[0] = FileOps.findFileOrThrow("$outDir/$newJar", "jphantom invocation failed")
    }

    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)
    }


    protected String cacheMeta() {
        Collection<String> inputJars = inputFiles.collect {
            File file -> file.toString()
        }
        Collection<String> cacheOptions = options.values().findAll {
            it.forCacheID
        }.collect {
            AnalysisOption option -> option.toString()
        }.sort()
        return (inputJars + cacheOptions).join("\n")
    }

    /**
     * Creates a new class loader for running jphantom
     */
    protected ClassLoader phantomClassLoader() {
        return copyOfCurrentClasspath()
    }
    /**
     * Creates a new class loader for running soot
     */
    protected ClassLoader sootClassLoader() {
        return copyOfCurrentClasspath()
    }

    protected ClassLoader copyOfCurrentClasspath() {
        URLClassLoader loader = this.getClass().getClassLoader() as URLClassLoader
        URL[] classpath = loader.getURLs()
        return new URLClassLoader(classpath, null as ClassLoader)
    }

    /**
     * Creates a new class loader for running averroes
     */
    protected ClassLoader averroesClassLoader() {
        //TODO: for now, we hard-code the averroes jar and properties
        String jar = "${Doop.doopHome}/lib/averroes-no-properties.jar"
        String properties = "$outDir/averroes.properties"

        //Determine the library jars
        Collection<String> libraryJars = inputFiles.drop(1).collect { it.toString() } + jreAverroesLibraries()

        //Create the averroes properties
        Properties props = new Properties()
        props.setProperty("application_includes", options.APP_REGEX.value as String)
        props.setProperty("main_class", options.MAIN_CLASS as String)
        props.setProperty("input_jar_files", inputFiles[0].toString())
        props.setProperty("library_jar_files", libraryJars.join(":"))

        //Concatenate the dynamic files
        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            File dynFileAll = new File(outDir, "all.dyn")
            dynFiles.each {String dynFile ->
                dynFileAll.append new File(dynFile).text
            }
            props.setProperty("dynamic_classes_file", dynFileAll.toString())
        }

        props.setProperty("tamiflex_facts_file", options.TAMIFLEX.value as String)
        props.setProperty("output_dir", averroesDir as String)
        props.setProperty("jre", javaAverroesLibrary())

        new File(properties).newWriter().withWriter { Writer writer ->
            props.store(writer, null)
        }

        def file1 = FileOps.findFileOrThrow(jar, "averroes jar missing or invalid: $jar")
        def file2 = FileOps.findFileOrThrow(properties, "averroes properties missing or invalid: $properties")

        List<URL> classpath = [file1.toURI().toURL(), file2.toURI().toURL()]
        return new URLClassLoader(classpath as URL[])
    }

    /**
     * Generates a list for the jre libs for averroes
     */
    protected List<String> jreAverroesLibraries() {

        def platformLibsValue = options.PLATFORM.value.toString().tokenize("_")
        assert platformLibsValue.size() == 2
        def (platform, version) = [platformLibsValue[0], platformLibsValue[1]]
        assert platform == "java"

        String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"

        //Not using if/else for readability
        switch(version) {
            case "1.3":
                return []
            case "1.4":
                return ["${path}/jce.jar", "${path}/jsse.jar"] as List<String>
            case "1.5":
                return ["${path}/jce.jar", "${path}/jsse.jar"] as List<String>
            case "1.6":
                return ["${path}/jce.jar", "${path}/jsse.jar"] as List<String>
            case "1.7":
                return ["${path}/jce.jar", "${path}/jsse.jar"] as List<String>
            case "1.8":
                return ["${path}/jce.jar", "${path}/jsse.jar"] as List<String>
            case "system":
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"] as List<String>
        }
    }

    /**
     * Generates the full path to the rt.jar required by averroes
     */
    protected String javaAverroesLibrary() {

        def platformLibsValue = options.PLATFORM.value.toString().tokenize("_")
        assert platformLibsValue.size() == 2
        def (platform, version) = [platformLibsValue[0], platformLibsValue[1]]
        assert platform == "java"

        String path = "${options.PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"
        return "$path/rt.jar"
    }

    protected void runHeapDL(String filename) {
        try {
            MemoryAnalyser memoryAnalyser = new MemoryAnalyser(filename, options.HEAPDL.value ? true : false)
            int n = memoryAnalyser.getAndOutputFactsToDB(factsDir, "2ObjH")
            logger.info("Generated " + n + " addditional facts from memory dump")
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
