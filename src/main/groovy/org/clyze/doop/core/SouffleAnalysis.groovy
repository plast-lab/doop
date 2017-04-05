package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.system.CheckSum
import org.clyze.doop.system.FileOps
import org.clyze.doop.input.InputResolutionContext

import static org.apache.commons.io.FileUtils.*


@CompileStatic
@TypeChecked
class SouffleAnalysis extends DoopAnalysis {
    long sootTime

    protected SouffleAnalysis(String id,
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
        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return


        copyDirectoryToDirectory(new File(Doop.souffleLogicPath + File.separator + "facts"), outDir)
        initDatabase()
        basicAnalysis()
        mainAnalysis()

    }

    @Override
    protected void generateFacts() {
        deleteQuietly(factsDir)
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

            runSoot()

            touch(new File(factsDir, "ApplicationClass.facts"))
            touch(new File(factsDir, "Properties.facts"))

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

            logger.info "Caching facts in $cacheDir"
            deleteQuietly(cacheDir)
            cacheDir.mkdirs()
            FileOps.copyDirContents(factsDir, cacheDir)
            new File(cacheDir, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
            logger.info "----"
        }

    }

    @Override
    protected void initDatabase() {
        cpp.preprocess("${outDir}/${name}.dl", "${Doop.souffleLogicPath}/facts/to-flow-sensitive.dl",
                                               "${Doop.souffleLogicPath}/facts/flow-sensitive-schema.dl",
                                               "${Doop.souffleLogicPath}/facts/flow-insensitive-schema.dl",
                                               "${Doop.souffleLogicPath}/facts/import-entities.dl",
                                               "${Doop.souffleLogicPath}/facts/import-facts.dl",
                                               "${Doop.souffleLogicPath}/facts/post-process.dl",
                                               "${Doop.souffleLogicPath}/facts/mock-heap.dl",
                                               "${Doop.souffleLogicPath}/facts/export.dl")

        if (options.TAMIFLEX.value) {
            def tamiflexDir = "${Doop.souffleAddonsPath}/tamiflex"
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/fact-declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/import.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${tamiflexDir}/post-import.dl")
        }
    }

    @Override
    protected void basicAnalysis() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleLogicPath}/basic/basic.dl", commonMacros)

        if (options.CFG_ANALYSIS.value || name == "sound-may-point-to") {
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/cfg-analysis/analysis.dl",
                    "${Doop.souffleAddonsPath}/cfg-analysis/declarations.dl")
        }

    }

    @Override
    protected void mainAnalysis() {
        def commonMacros = "${Doop.souffleLogicPath}/commonMacros.dl"
        def macros       = "${Doop.souffleAnalysesPath}/${name}/macros.dl"
        def mainPath     = "${Doop.souffleLogicPath}/main"
        def analysisPath = "${Doop.souffleAnalysesPath}/${name}"

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

        if (name == "sound-may-point-to") {
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/string-constants.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/exceptions.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", /*"${analysisPath}/declarations.dl",*/
                    "${mainPath}/context-sensitivity-declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl")
        }
        else {
            if (isContextSensitive) {
                cpp.includeAtStart("${outDir}/${name}.dl", /*"${analysisPath}/declarations.dl",*/
                        "${mainPath}/context-sensitivity-declarations.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/delta.dl",
                        commonMacros, "${mainPath}/main-delta.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl",
                        commonMacros, macros, "${mainPath}/context-sensitivity.dl")
            } else {
                cpp.includeAtStart("${outDir}/${name}-declarations.dl", "${analysisPath}/declarations.dl")
                cpp.includeAtStart("${outDir}/prologue.dl", "${mainPath}/prologue.dl", commonMacros)
                cpp.includeAtStart("${outDir}/${name}-prologue.dl", "${analysisPath}/prologue.dl")
                cpp.includeAtStart("${outDir}/${name}-delta.dl", "${analysisPath}/delta.dl")
                cpp.includeAtStart("${outDir}/${name}.dl", "${analysisPath}/analysis.dl")
            }

        }
        if (options.REFLECTION.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${mainPath}/reflection/delta.dl")
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value)
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/dacapo/rules.dl", commonMacros)

        if (options.TAMIFLEX.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/declarations.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/delta.dl")
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/tamiflex/rules.dl", commonMacros)
        }

        if (options.SANITY.value)
            cpp.includeAtStart("${outDir}/${name}.dl", "${Doop.souffleAddonsPath}/sanity.dl")

        if (options.MAIN_CLASS.value) {
            def analysisFile = FileOps.findFileOrThrow("${outDir}/${name}.dl", "Missing ${outDir}/${name}.dl")
            analysisFile.append("""MainClass("${options.MAIN_CLASS.value}").\n""")
        }

        runSouffle(Integer.parseInt(options.JOBS.value.toString()), factsDir, outDir)
    }

    private void runSouffle(int jobs, File factsDir, File outDir) {


        def analysisChecksum = CheckSum.checksum(new File("${outDir}/${name}.dl"), DoopAnalysisFactory.HASH_ALGO)
        def analysisCacheDir = new File("${Doop.souffleAnalysesCache}/${analysisChecksum}")

        if (!analysisCacheDir.exists()) {
            logger.info "Compiling datalog to produce C++ program and executable with souffle"
            def commandLine = "souffle -c -w -o ${outDir}/${name} ${outDir}/${name}.dl -p$outDir.absolutePath/profile.txt"
            if (options.SOUFFLE_DEBUG_REPORT.value)
                commandLine += " -r$outDir.absolutePath/report.html"
            logger.info "Souffle command: ${commandLine}"

            long t = timing {
                executor.execute(commandLine)
            }

            logger.info "Compilation time (sec): ${t}"

            // The analysis executable is created at the directory level of the doop invocation so we have to move it under the outDir
            analysisCacheDir.mkdirs()
            executor.execute("mv ${name} ${analysisCacheDir}")
            logger.info "Running analysis executable"
        }
        else {
            logger.info "Running cached analysis executable"
        }

        long t = timing {
            System.out.println("${analysisCacheDir}/${name} -j$jobs -F$factsDir.absolutePath -D$outDir.absolutePath -p$outDir.absolutePath/profile.txt")
            executor.execute("${analysisCacheDir}/${name} -j$jobs -F$factsDir.absolutePath -D$outDir.absolutePath -p$outDir.absolutePath/profile.txt")
        }
        logger.info "Analysis execution time (sec): ${t}"
    }

    @Override
    protected void produceStats() {

        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
        }
        // Special case of X_STATS_AROUND (detected automatically)
        def specialStats       = new File("${Doop.souffleAnalysesPath}/${name}/statistics.dl")
        if (specialStats.exists()) {
            cpp.includeAtStart("${outDir}/${name}.dl", specialStats.toString())
            return
        }

        def macros    = "${Doop.souffleAnalysesPath}/${name}/macros.logic"
        def statsPath = "${Doop.souffleAddonsPath}/statistics"
        cpp.includeAtStart("${outDir}/${name}.dl", "${statsPath}/statistics-simple.dl", macros)

        if (options.X_STATS_FULL.value) {
            cpp.includeAtStart("${outDir}/${name}.dl", "${statsPath}/statistics.dl", macros)
        }

    }



    @Override
    protected void runSoot() {
        deleteQuietly(outDir)
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
            depArgs = platformLibs.collect{ lib -> ["-l", lib.toString()]}.flatten() + deps
        }

        Collection<String> params = null

        switch(platform) {
            case "java":
                params = ["--full"] + depArgs + ["--application-regex", options.APP_REGEX.value.toString()]
                break
            case "android":
                // This uses all platformLibs.
                // params = ["--full"] + depArgs + ["--android-jars"] + platformLibs.collect({ f -> f.getAbsolutePath() })
                // This uses just platformLibs[0], assumed to be android.jar.
                params = ["--full"] + depArgs + ["--android-jars"] +
                        [platformLibs[0].getAbsolutePath()]
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

        logger.info "Fact generation time: ${sootTime}"
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

    @Override
    protected void runTransformInput() {

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
}
