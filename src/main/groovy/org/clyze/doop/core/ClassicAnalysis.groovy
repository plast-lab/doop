package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.*
import org.clyze.doop.dynamicanalysis.MemoryAnalyser
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.datalog.*
import org.clyze.doop.system.*

/**
 * A classic (may, unsound) DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 *
 * For supporting invocations over the web, the statistic step is broken into
 * two parts: (a) produce statistics and (b) print statistics.
 *
 * The run() method is the entry point. No other methods should be called directly by other classes.
 */
@CompileStatic
@TypeChecked
class ClassicAnalysis extends DoopAnalysis {

    boolean isRefineStep

    long sootTime

    protected ClassicAnalysis(String id,
                              String name,
                              Map<String, AnalysisOption> options,
                              InputResolutionContext ctx,
                              File outDir,
                              File cacheDir,
                              List<File> inputFiles,
                              List<File> platformLibs,
                              Map<String, String> commandsEnvironment) {
        super(id, name, options, ctx, outDir, cacheDir, inputFiles, platformLibs, commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }
    }

    String toString() {
        return [id:id, name:name, outDir:outDir, cacheDir:cacheDir, inputFiles:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    @Override
    void run() {
        //Initialize the instance here and not in the constructor, in order to allow an analysis to be re-runnable.
        connector = new LBWorkspaceConnector(outDir,
                                             options.BLOXBATCH.value as String,
                                             (options.BLOX_OPTS.value ?: '') as String,
                                             executor, cpp)

        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return

        initDatabase()
        if (!options.X_STOP_AT_INIT.value) {

            basicAnalysis()
            if (!options.X_STOP_AT_BASIC.value) {

                mainAnalysis()

                try {
                    FileOps.findFileOrThrow("${Doop.analysesPath}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
                    reanalyze()
                }
                catch(e) {
                    logger.debug e.getMessage()
                }

                produceStats()
            }
        }

        logger.info "\nAnalysis START"
        long t = timing { connector.processQueue() }
        logger.info "Analysis END\n"
        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
        connector
            .connect(database.toString())
            .addBlock("""Stats:Runtime("script wall-clock time (sec)", $t).
                         Stats:Runtime("disk footprint (KB)", $dbSize).""")
    }


    @Override
    protected void generateFacts() {
        FileUtils.deleteQuietly(factsDir)
        factsDir.mkdirs()

        if (cacheDir.exists() && options.CACHE.value) {
            logger.info "Using cached facts from $cacheDir"
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

            if (options.ANALYZE_MEMORY_DUMP.value) {
                analyseMemoryDump(options.ANALYZE_MEMORY_DUMP)
            }

            runSoot()

            FileUtils.touch(new File(factsDir, "ApplicationClass.facts"))
            FileUtils.touch(new File(factsDir, "Properties.facts"))

            if (options.TAMIFLEX.value) {
                File origTamFile  = new File(options.TAMIFLEX.value.toString())

                new File(factsDir, "Tamiflex.facts").withWriter { w ->
                    origTamFile.eachLine { line ->
                        w << line
                                .replaceFirst(/;[^;]*;$/, "")
                                .replaceFirst(/;$/, ";0")
                                .replaceFirst(/(^.*;.*)\.([^.]+;[0-9]+$)/) { full, first, second -> first+";"+second+"\n" }
                    }
                }
            }

            logger.info "Caching facts in $cacheDir"
            FileUtils.deleteQuietly(cacheDir)
            cacheDir.mkdirs()
            FileOps.copyDirContents(factsDir, cacheDir)
            new File(cacheDir, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
            logger.info "----"
        }
    }

    @Override
    protected void initDatabase() {
        def commonMacros = "${Doop.logicPath}/commonMacros.logic"

        FileUtils.deleteQuietly(database)
        cpp.preprocess("${outDir}/flow-sensitive-schema.logic", "${Doop.factsPath}/flow-sensitive-schema.logic")
        cpp.preprocess("${outDir}/flow-insensitive-schema.logic", "${Doop.factsPath}/flow-insensitive-schema.logic")
        cpp.preprocess("${outDir}/import-entities.logic", "${Doop.factsPath}/import-entities.logic")
        cpp.preprocess("${outDir}/import-facts.logic", "${Doop.factsPath}/import-facts.logic")
        cpp.preprocess("${outDir}/to-flow-insensitive-delta.logic", "${Doop.factsPath}/to-flow-insensitive-delta.logic")
        cpp.preprocess("${outDir}/post-process.logic", "${Doop.factsPath}/post-process.logic", commonMacros)

        connector.queue()
            .createDB(database.getName())
            .timedTransaction("-- Init DB (import) --")
            .addBlockFile("flow-sensitive-schema.logic")
            .addBlockFile("flow-insensitive-schema.logic")
            .executeFile("import-entities.logic")
            .executeFile("import-facts.logic")

        if (options.TAMIFLEX.value) {
            def tamiflexDir = "${Doop.addonsPath}/tamiflex"
            cpp.preprocess("${outDir}/tamiflex-fact-declarations.logic", "${tamiflexDir}/fact-declarations.logic")
            cpp.preprocess("${outDir}/tamiflex-import.logic", "${tamiflexDir}/import.logic")
            cpp.preprocess("${outDir}/tamiflex-post-import.logic", "${tamiflexDir}/post-import.logic")

            connector.queue()
                .addBlockFile("tamiflex-fact-declarations.logic")
                .executeFile("tamiflex-import.logic")
                .addBlockFile("tamiflex-post-import.logic")
        }

        if (options.MAIN_CLASS.value)
            connector.queue().addBlock("""MainClass(x) <- ClassType(x), Type:Id(x:"${options.MAIN_CLASS.value}").""")

        connector.queue()
            .addBlock("""Stats:Runtime("soot-fact-generation time (sec)", $sootTime).""")
            .commit()
            .elapsedTime()
            .timedTransaction("-- Init DB (post) --")
            .addBlockFile("post-process.logic")
            .commit()
            .elapsedTime()
            .timedTransaction("-- Init DB (flow-ins) --")
            .executeFile("to-flow-insensitive-delta.logic")
            .commit()
            .elapsedTime()

        if (options.TRANSFORM_INPUT.value)
            runTransformInput()
    }

    @Override
    protected void basicAnalysis() {
        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            dynFiles.eachWithIndex { String dynFile, Integer index ->
                File f = new File(dynFile)
                File dynImport = new File(outDir, "dynamic${index}.import")
                FileOps.writeToFile dynImport, """\
                                              option,delimiter,"\t"
                                              option,hasColumnNames,false

                                              fromFile,"${f.getCanonicalPath()}",a,inv,b,type
                                              toPredicate,Config:DynamicClass,type,inv
                                              """.toString().stripIndent()

                connector.queue().eval("import -f $dynImport")
            }
        }

        def commonMacros = "${Doop.logicPath}/commonMacros.logic"
        cpp.preprocess("${outDir}/basic.logic", "${Doop.logicPath}/basic/basic.logic", commonMacros)

        connector.queue()
            .timedTransaction("-- Basic Analysis --")
            .addBlockFile("basic.logic")

        if (options.CFG_ANALYSIS.value) {
            cpp.preprocess("${outDir}/cfg-analysis.logic", "${Doop.addonsPath}/cfg-analysis/analysis.logic",
                             "${Doop.addonsPath}/cfg-analysis/declarations.logic")
            connector.queue().addBlockFile("cfg-analysis.logic")
        }

        connector.queue()
            .commit()
            .elapsedTime()
    }

    @Override
    protected void mainAnalysis() {
        def commonMacros = "${Doop.logicPath}/commonMacros.logic"
        def macros       = "${Doop.analysesPath}/${name}/macros.logic"
        def mainPath     = "${Doop.logicPath}/main"
        def analysisPath = "${Doop.analysesPath}/${name}"

        // By default, assume we run a context-sensitive analysis
        boolean isContextSensitive = true
        try {
            def file = FileOps.findFileOrThrow("${analysisPath}/analysis.properties", "No analysis.properties for ${name}")
            Properties props = FileOps.loadProperties(file)
            isContextSensitive = props.getProperty("is_context_sensitive").toBoolean()
        }
        catch(e) {
            logger.debug e.getMessage()
        }
        if (isContextSensitive) {
            cpp.preprocessIfExists("${outDir}/${name}-declarations.logic", "${analysisPath}/declarations.logic",
                             "${mainPath}/context-sensitivity-declarations.logic")
            cpp.preprocess("${outDir}/prologue.logic", "${mainPath}/prologue.logic", commonMacros)
            cpp.preprocessIfExists("${outDir}/${name}-delta.logic", "${analysisPath}/delta.logic",
                             commonMacros, "${mainPath}/main-delta.logic")
            cpp.preprocess("${outDir}/${name}.logic", "${analysisPath}/analysis.logic",
                             commonMacros, macros, "${mainPath}/context-sensitivity.logic")
        }
        else {
            cpp.preprocess("${outDir}/${name}-declarations.logic", "${analysisPath}/declarations.logic")
            cpp.preprocessIfExists("${outDir}/prologue.logic", "${mainPath}/prologue.logic", commonMacros)
            cpp.preprocessIfExists("${outDir}/${name}-prologue.logic", "${analysisPath}/prologue.logic")
            cpp.preprocessIfExists("${outDir}/${name}-delta.logic", "${analysisPath}/delta.logic")
            cpp.preprocess("${outDir}/${name}.logic", "${analysisPath}/analysis.logic")
        }

        connector.queue()
            .timedTransaction("-- Prologue --")
            .addBlockFile("${name}-declarations.logic")
            .addBlockFile("prologue.logic")
            .commit()
            .elapsedTime()
            .timedTransaction("-- Main Deltas -- ")
            .executeFile("${name}-delta.logic")

        if (options.REFLECTION.value) {
            cpp.preprocess("${outDir}/reflection-delta.logic", "${mainPath}/reflection/delta.logic")

            connector.queue()
                .commit()
                .transaction()
                .executeFile("reflection-delta.logic")
                .commit()
                .transaction()
        }

        /**
         * Generic file for incrementally adding addons logic from various
         * points. This is necessary in some cases to avoid weird errors from
         * the engine (DELTA_RECURSION etc.) and in general it helps
         * performance-wise.
         */
        File addons = new File(outDir, "addons.logic")
        FileUtils.deleteQuietly(addons)
        FileUtils.touch(addons)

        String echo_analysis = "Pointer Analysis"
        
        if (options.INFORMATION_FLOW.value) {
            echo_analysis = "Pointer and Information-flow Analysis"
            cpp.preprocess("${outDir}/information-flow-declarations.logic", "${Doop.addonsPath}/information-flow/declarations.logic")
            cpp.preprocess("${outDir}/information-flow-delta.logic", "${Doop.addonsPath}/information-flow/delta.logic", macros)
            cpp.preprocess("${outDir}/information-flow-rules.logic", "${Doop.addonsPath}/information-flow/rules.logic", macros)
            cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/information-flow-rules.logic")

            cpp.preprocess("${outDir}/sources-and-sinks.logic", "${Doop.addonsPath}/information-flow/${options.INFORMATION_FLOW.value}-sources-and-sinks.logic", macros)
                cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/sources-and-sinks.logic")

            connector.queue()
                .addBlockFile("information-flow-declarations.logic")
                .commit()
                .transaction()
                .executeFile("information-flow-delta.logic")
                .commit()
                .transaction()
        }

        if (options.OPEN_PROGRAMS.value) {
            cpp.preprocess("${outDir}/open-programs.logic", "${Doop.addonsPath}/open-programs/rules-${options.OPEN_PROGRAMS.value}.logic", macros)
            cpp.includeAtStart("${outDir}/addons.logic", "${outDir}/open-programs.logic")

        }

        if (options.DACAPO.value || options.DACAPO_BACH.value)
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/dacapo/rules.logic", commonMacros)

        if (options.TAMIFLEX.value) {
            cpp.preprocess("${outDir}/tamiflex-declarations.logic", "${Doop.addonsPath}/tamiflex/declarations.logic")
            cpp.preprocess("${outDir}/tamiflex-delta.logic", "${Doop.addonsPath}/tamiflex/delta.logic")
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/tamiflex/rules.logic", commonMacros)

            connector.queue()
                .addBlockFile("tamiflex-declarations.logic")
                .executeFile("tamiflex-delta.logic")
        }

        if (options.SANITY.value)
            cpp.includeAtStart("${outDir}/addons.logic", "${Doop.addonsPath}/sanity.logic")

        cpp.includeAtStart("${outDir}/${name}.logic", "${outDir}/addons.logic")

        connector.queue()
            .commit()
            .elapsedTime()

        if (isRefineStep) importRefinement()

        connector.queue()
            .timedTransaction("-- " + echo_analysis + " --")
            .addBlockFile("${name}.logic")
            .commit()
            .elapsedTime()

        if (options.MUST.value) {
            cpp.preprocess("${outDir}/must-point-to-may-pre-analysis.logic", "${Doop.analysesPath}/must-point-to/may-pre-analysis.logic")
            cpp.preprocess("${outDir}/must-point-to.logic", "${Doop.analysesPath}/must-point-to/analysis-simple.logic")

            connector.queue()
                .echo("-- Pre Analysis (for Must) --")
                .startTimer()
                .transaction()
                .addBlockFile("must-point-to-may-pre-analysis.logic")
                .addBlock("RootMethodForMustAnalysis(?meth) <- Method:DeclaringType[?meth] = ?class, ApplicationClass(?class), Reachable(?meth).")
                .commit()
                .elapsedTime()
                .echo("-- Must Analysis --")
                .startTimer()
                .transaction()
                .addBlockFile("must-point-to.logic")
                .commit()
                .elapsedTime()
        }
    }

    @Override
    protected void produceStats() {
        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
            connector.queue().include(options.X_STATS_AROUND.value as String)
            return
        }
        // Special case of X_STATS_AROUND (detected automatically)
        def specialStats       = new File("${Doop.analysesPath}/${name}/statistics.logic")
        def specialStatsScript = new File("${Doop.analysesPath}/${name}/statistics.part.lb")
        if (specialStats.exists() && specialStatsScript.exists()) {
            cpp.preprocess("${outDir}/statistics.logic", specialStats.toString())
            connector.queue().include(specialStatsScript.toString())
            return
        }

        def macros    = "${Doop.analysesPath}/${name}/macros.logic"
        def statsPath = "${Doop.addonsPath}/statistics"
        cpp.preprocess("${outDir}/statistics-simple.logic", "${statsPath}/statistics-simple.logic", macros)

        connector.queue()
            .timedTransaction("-- Statistics --")
            .addBlockFile("statistics-simple.logic")

        if (options.X_STATS_FULL.value) {
            cpp.preprocess("${outDir}/statistics.logic", "${statsPath}/statistics.logic", macros)
            connector.queue().addBlockFile("statistics.logic")
        }

        connector.queue()
            .commit()
            .elapsedTime()
    }

    @Override
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
            Collection<String> deps = inputFiles.drop(1).collect{ File f -> ["-l", f.toString()]}.flatten() as Collection<String>
            depArgs = platformLibs.collect{ lib -> ["-l", lib.toString()]}.flatten() +  deps
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

        if (options.X_DRY_RUN.value) {
            params += ["--noFacts"]
        }

        params = params + ["-d", factsDir.toString(), inputFiles[0].toString()]

        logger.debug "Params of soot: ${params.join(' ')}"

        sootTime = timing {
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
    }

    @Override
    protected void runTransformInput() {
        cpp.preprocess("${outDir}/transform.logic", "${Doop.addonsPath}/transform/rules.logic", "${Doop.addonsPath}/transform/declarations.logic")
        connector.queue()
            .echo("-- Transforming Facts --")
            .startTimer()
            .transaction()
            .addBlockFile("${outDir}/transform.logic")
            .commit()

        2.times { int i ->
            connector.queue()
                .echo(""" "-- Transformation (step $i) --" """)
                .transaction()
                .executeFile("${Doop.addonsPath}/transform/delta.logic")
                .commit()
        }
        connector.queue().elapsedTime()
    }

    @Override
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

    @Override
    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)
    }

    protected void analyseMemoryDump(String filename) {
        logger.info("-- Analysing Memory Dump --")
        MemoryAnalyser memoryAnalyser = new MemoryAnalyser(filename)
        memoryAnalyser.factsFromDump()

    }


    private void reanalyze() {
        cpp.preprocess("${outDir}/refinement-delta.logic", "${Doop.analysesPath}/${name}/refinement-delta.logic")
        cpp.preprocess("${outDir}/export-refinement.logic", "${Doop.logicPath}/main/export-refinement.logic")
        cpp.preprocess("${outDir}/import-refinement.logic", "${Doop.logicPath}/main/import-refinement.logic")

        connector.queue()
            .echo("++++ Refinement ++++")
            .echo("-- Export --")
            .startTimer()
            .transaction()
            .executeFile("refinement-delta.logic")
            .commit()
            .transaction()
            .executeFile("export-refinement.logic")
            .commit()
            .elapsedTime()

        isRefineStep = true
        initDatabase()
        basicAnalysis()
        mainAnalysis()
    }

    private void importRefinement() {
        connector.queue()
            .echo("-- Import --")
            .startTimer()
            .transaction()
            .executeFile("import-refinement.logic")
            .commit()
            .elapsedTime()
    }


    private String cacheMeta() {
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
    private ClassLoader phantomClassLoader() {
        return copyOfCurrentClasspath()
    }

    /**
     * Creates a new class loader for running soot
     */
    private ClassLoader sootClassLoader() {
        return copyOfCurrentClasspath()
    }

    private ClassLoader copyOfCurrentClasspath() {
        URLClassLoader loader = this.getClass().getClassLoader() as URLClassLoader
        URL[] classpath = loader.getURLs()
        return new URLClassLoader(classpath, null as ClassLoader)
    }

    /**
     * Creates a new class loader for running averroes
     */
    private ClassLoader averroesClassLoader() {
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
    private List<String> jreAverroesLibraries() {

        def platformLibsValue = options.PLATFORM.value.toString().tokenize("_")
        assert platformLibsValue.size() == 2
        def (platform, version) = [platformLibsValue[0], platformLibsValue[1]]
        assert platform == "java"

        String path = "${options.DOOP_PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"

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
            case "system":
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"] as List<String>
        }
    }

    /**
     * Generates the full path to the rt.jar required by averroes
     */
    private String javaAverroesLibrary() {

        def platformLibsValue = options.PLATFORM.value.toString().tokenize("_")
        assert platformLibsValue.size() == 2
        def (platform, version) = [platformLibsValue[0], platformLibsValue[1]]
        assert platform == "java"

        String path = "${options.DOOP_PLATFORMS_LIB.value}/JREs/jre1.${version}/lib"
        return "$path/rt.jar"
    }

    Iterable<AnalysisPhase> phases() { return null }
}
