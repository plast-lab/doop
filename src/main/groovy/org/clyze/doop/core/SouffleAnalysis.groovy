package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.system.FileOps
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.datalog.*
import org.clyze.doop.system.*


@CompileStatic
@TypeChecked
class SouffleAnalysis extends Analysis {
    long sootTime

    protected SouffleAnalysis(String id,
                              String outDirPath,
                              String cacheDirPath,
                              String name,
                              Map<String, AnalysisOption> options,
                              InputResolutionContext ctx,
                              List<File> inputs,
                              List<File> platformLibs,
                              Map<String, String> commandsEnvironment) {
        super(id, outDirPath, cacheDirPath, name, options, ctx, inputs, platformLibs, commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }
    }

    String toString() {
        return [id:id, name:safename, outDir:outDir, cacheDir:cacheDir, inputs:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
                "\n" +
                options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    @Override
    void run() {
        generateFacts()
        if (options.X_STOP_AT_FACTS.value) return

        //runSouffle


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

    }

    @Override
    protected void basicAnalysis() {

    }

    @Override
    protected void mainAnalysis() {

    }

    @Override
    protected void produceStats() {

    }

    private void runSouffle(int jobs, String factsDir, String outDir) {
        executor.execute("souffle -j$jobs -F$factsDir -D$outDir")
    }

    @Override
    protected void runSoot() {
        Collection<String> depArgs

        def platform = options.PLATFORM.value.toString().tokenize("_")[0]
        assert platform == "android" || platform == "java"

        if (options.RUN_AVERROES.value) {
            //change linked arg and injar accordingly
            inputs[0] = FileOps.findFileOrThrow("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar".toString()]
        }
        else {
            Collection<String> deps = inputs.drop(1).collect{ File f -> ["-l", f.toString()]}.flatten() as Collection<String>
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
            params = params + ["--ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params = params + ["--allow-phantom"]
        }

        if (options.RUN_FLOWDROID.value) {
            params = params + ["--run-flowdroid"]
        }

        if (options.ONLY_APPLICATION_CLASSES_FACT_GEN.value) {
            params = params + ["--only-application-classes-fact-gen"]
        }

        params = params + ["-d", factsDir.toString(), inputs[0].toString()]

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


    private String cacheMeta() {
        Collection<String> inputJars = inputs.collect {
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
        Collection<String> libraryJars = inputs.drop(1).collect { it.toString() } + jreAverroesLibraries()

        //Create the averroes properties
        Properties props = new Properties()
        props.setProperty("application_includes", options.APP_REGEX.value as String)
        props.setProperty("main_class", options.MAIN_CLASS as String)
        props.setProperty("input_jar_files", inputs[0].toString())
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

        String jar = inputs[0].toString()
        String jarName = FilenameUtils.getBaseName(jar)
        String jarExt = FilenameUtils.getExtension(jar)
        String newJar = "${jarName}-complemented.${jarExt}"
        String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
        logger.debug "Params of jphantom: ${params.join(' ')}"

        //we invoke the main method reflectively to avoid adding jphantom as a compile-time dependency
        ClassLoader loader = phantomClassLoader()
        Helper.execJava(loader, "org.clyze.jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        inputs[0] = FileOps.findFileOrThrow("$outDir/$newJar", "jphantom invocation failed")
    }

    @Override
    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)
    }
}
