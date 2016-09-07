package org.clyze.doop.core

import groovy.transform.TypeChecked
import java.util.regex.Pattern
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.blox.BloxbatchConnector
import org.clyze.doop.blox.BloxbatchScript
import org.clyze.doop.blox.WorkspaceConnector
import org.clyze.doop.input.InputResolutionContext
import org.clyze.doop.system.*

import static org.clyze.doop.system.CppPreprocessor.*

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 *
 * For supporting invocations over the web, the statistic step is broken into
 * two parts: (a) produce statistics and (b) print statistics.
 *
 * The run() method is the only public method exposed by this class: no other methods should be called directly
 * by other classes.
 */
@TypeChecked
class Analysis implements Runnable {

    protected Log logger = LogFactory.getLog(getClass())

    /**
     * The unique identifier of the analysis (that determines the caching)
     */
    String id

    /**
     * The name of the analysis (that determines the logic)
     */
    String name

    /**
     * The name of the analysis stripped of directory separators
     */
    String safename

    /**
     * The output dir for the analysis
     */
    String outDir

    /**
     * The cache dir for the input facts
     */
    String cacheDir

    /**
     * The options of the analysis
     */
    Map<String, AnalysisOption> options

    /**
     * The analysis input resolution mechanism
     */
    InputResolutionContext ctx

    /**
     * The input jar files/dependencies of the analysis
     */
    List<File> inputJarFiles

    /**
     * The jre library jars for soot
     */
    List<String> jreJars

    /**
     * The environment for running external commands
     */
    Map<String, String> commandsEnvironment

    boolean isRefineStep

    Executor executor

    File facts, cacheFacts, database, averroesDir

    BloxbatchScript lbScript

    long sootTime

    WorkspaceConnector connector

    private static final List<String> IGNORED_WARNINGS = [
        """\
        *******************************************************************
        Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.
        Please use 'lb' instead of 'bloxbatch'.
        *******************************************************************
        """
    ].collect{ line -> Pattern.quote(line.stripIndent()) }

    /*
     * Use a java-way to construct the instance (instead of using Groovy's automatically generated Map constructor)
     * in order to ensure that internal state is initialized at one point and the init method is no longer required.
     * This new constructor embodies the old init method and offers a ready-to-use analysis object.
     */
    protected Analysis(String id,
                       String outDir,
                       String cacheDir,
                       String name,
                       Map<String, AnalysisOption> options,
                       InputResolutionContext ctx,
                       List<File> inputJarFiles,
                       List<String> jreJars,
                       Map<String, String> commandsEnvironment) {
        this.id = id
        this.outDir = outDir
        this.cacheDir = cacheDir
        this.name = name
        this.safename = name.replace(File.separator, "-")
        this.options = options
        this.ctx = ctx
        this.inputJarFiles = inputJarFiles
        this.jreJars = jreJars
        this.commandsEnvironment = commandsEnvironment

        executor = new Executor(commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }

        facts       = new File(outDir, "facts")
        cacheFacts  = new File(cacheDir)
        database    = new File(outDir, "database")
        averroesDir = new File(outDir, "averroes")

        // Create workspace connector (needed by the post processor and the server-side analysis execution)
        connector = new BloxbatchConnector(database, commandsEnvironment)
    }

    @Override
    void run() {
        /*
         Initialize the writer here and not in the constructor, in order to allow an analysis to be re-run.
         */
        lbScript    = new BloxbatchScript(new File(outDir, "run.lb"))

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

        lbScript.close()

        logger.info "Using generated script ${lbScript.getPath()}"
        logger.info "\nAnalysis START"
        long t = timing {
             def bloxOpts = options.BLOX_OPTS.value ?: ''
             executor.execute(outDir, "${options.BLOXBATCH.value} -script ${lbScript.getPath()} $bloxOpts", IGNORED_WARNINGS)
        }
        logger.info "Analysis END\n"
        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
        bloxbatch database, """-addBlock 'Stats:Runtime("script wall-clock time (sec)", $t).
                                          Stats:Runtime("disk footprint (KB)", $dbSize).'"""
    }


    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:safename, outDir:outDir, cacheDir:cacheDir, inputs:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    protected void generateFacts() {

        FileUtils.deleteQuietly(facts)
        facts.mkdirs()

        if (cacheFacts.exists() && options.CACHE.value) {
            logger.info "Using cached facts from $cacheFacts"
            FileOps.copyDirContents(cacheFacts, facts)
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

            FileUtils.touch(new File(facts, "ApplicationClass.facts"))
            FileUtils.touch(new File(facts, "Properties.facts"))

            if (options.TAMIFLEX.value) {
                File origTamFile  = new File(options.TAMIFLEX.value.toString())

                new File(facts, "Tamiflex.facts").withWriter { w ->
                    origTamFile.eachLine { line ->
                        w << line
                                .replaceFirst(/;[^;]*;$/, "")
                                .replaceFirst(/;$/, ";0")
                                .replaceFirst(/(^.*;.*)\.([^.]+;[0-9]+$)/) { full, first, second -> first+";"+second+"\n" }
                    }
                }
            }

            logger.info "Caching facts in $cacheFacts"
            FileUtils.deleteQuietly(cacheFacts)
            cacheFacts.mkdirs()
            FileOps.copyDirContents(facts, cacheFacts)
            new File(cacheFacts, "meta").withWriter { BufferedWriter w -> w.write(cacheMeta()) }
        }
    }

    private String cacheMeta() {
        Collection<String> inputJars = inputJarFiles.collect {
            File file -> file.toString()
        }
        Collection<String> cacheOptions = options.values().findAll {
            it.forCacheID
        }.collect {
            AnalysisOption option -> option.toString()
        }.sort()
        return (inputJars + cacheOptions).join("\n")
    }

    protected void initDatabase() {

        FileUtils.deleteQuietly(database)
        preprocess(this, "${outDir}/flow-sensitive-schema.logic", "${Doop.factsPath}/flow-sensitive-schema.logic")
        preprocess(this, "${outDir}/flow-insensitive-schema.logic", "${Doop.factsPath}/flow-insensitive-schema.logic")
        preprocess(this, "${outDir}/import-entities.logic", "${Doop.factsPath}/import-entities.logic")
        preprocess(this, "${outDir}/import-facts.logic", "${Doop.factsPath}/import-facts.logic")
        preprocess(this, "${outDir}/to-flow-insensitive-delta.logic", "${Doop.factsPath}/to-flow-insensitive-delta.logic")
        preprocess(this, "${outDir}/post-process.logic", "${Doop.factsPath}/post-process.logic")

        lbScript
            .createDB(database.getName())
            .echo("-- Init DB --")
            .startTimer()
            .transaction()
            .addBlockFile("flow-sensitive-schema.logic")
            .addBlockFile("flow-insensitive-schema.logic")
            .addBlock("""Stats:Runtime("soot-fact-generation time (sec)", $sootTime).""")
            .executeFile("import-entities.logic")
            .executeFile("import-facts.logic")
            .executeFile("to-flow-insensitive-delta.logic")

        if (options.TAMIFLEX.value) {
            def tamiflexDir = "${Doop.addonsPath}/tamiflex"
            preprocess(this, "${outDir}/tamiflex-fact-declarations.logic", "${tamiflexDir}/fact-declarations.logic")
            preprocess(this, "${outDir}/tamiflex-import.logic", "${tamiflexDir}/import.logic")
            preprocess(this, "${outDir}/tamiflex-post-import.logic", "${tamiflexDir}/post-import.logic")

            lbScript
                .addBlockFile("tamiflex-fact-declarations.logic")
                .executeFile("tamiflex-import.logic")
                .addBlockFile("tamiflex-post-import.logic")
        }

        if (options.MAIN_CLASS.value)
            lbScript.addBlock("""MainClass(x) <- ClassType(x), Type:Value(x:"${options.MAIN_CLASS.value}").""")

        lbScript
            .commit()
            .transaction()
            .addBlockFile("post-process.logic")
            .commit()
            .elapsedTime()

        if (options.TRANSFORM_INPUT.value)
            runTransformInput()
    }

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

                lbScript.wr("import -f $dynImport")
            }
        }

        def factMacros = "${Doop.factsPath}/macros.logic"
        preprocess(this, "${outDir}/basic.logic", "${Doop.logicPath}/basic/basic.logic", factMacros)

        lbScript
            .echo("-- Basic Analysis --")
            .startTimer()
            .transaction()
            .addBlockFile("basic.logic")

        if (options.CFG_ANALYSIS.value) {
            preprocess(this, "${outDir}/cfg-analysis.logic", "${Doop.addonsPath}/cfg-analysis/analysis.logic",
                             "${Doop.addonsPath}/cfg-analysis/declarations.logic")
            lbScript.addBlockFile("cfg-analysis.logic")
        }

        lbScript
            .commit()
            .elapsedTime()
    }

    /**
     * Performs the main part of the analysis.
     */
    protected void mainAnalysis() {
        def factMacros   = "${Doop.factsPath}/macros.logic"
        def macros       = "${Doop.analysesPath}/${name}/macros.logic"
        def mainPath     = "${Doop.logicPath}/main"
        def analysisPath = "${Doop.analysesPath}/${name}"

        // By default, assume we run a context-sensitive analysis
        boolean isContextSensitive = true
        try {
            def file = FileOps.findFileOrThrow("${analysisPath}/analysis.properties", "No analysis.properties for ${safename}")
            Properties props = FileOps.loadProperties(file)
            isContextSensitive = props.getProperty("is_context_sensitive").toBoolean()
        }
        catch(e) {
            logger.debug e.getMessage()
        }
        if (isContextSensitive) {
            preprocess(this, "${outDir}/${safename}-declarations.logic", "${analysisPath}/declarations.logic",
                             "${mainPath}/context-sensitivity-declarations.logic")
            preprocess(this, "${outDir}/prologue.logic", "${mainPath}/prologue.logic")
            preprocessIfExists(this, "${outDir}/${safename}-prologue.logic", "${analysisPath}/prologue.logic")
            preprocessIfExists(this, "${outDir}/${safename}-delta.logic", "${analysisPath}/delta.logic",
                             factMacros, "${mainPath}/main-delta.logic")
            preprocess(this, "${outDir}/${safename}.logic", "${analysisPath}/analysis.logic",
                             factMacros, macros, "${mainPath}/context-sensitivity.logic")
        }
        else {
            preprocess(this, "${outDir}/${safename}-declarations.logic", "${analysisPath}/declarations.logic")
            preprocessIfExists(this, "${outDir}/${safename}-prologue.logic", "${analysisPath}/prologue.logic")
            preprocessIfExists(this, "${outDir}/${safename}-delta.logic", "${analysisPath}/delta.logic")
            preprocess(this, "${outDir}/${safename}.logic", "${analysisPath}/analysis.logic")
        }

        lbScript
            .echo("-- Prologue --")
            .startTimer()
            .transaction()
            .addBlockFile("${safename}-declarations.logic")
            .addBlockFile("prologue.logic")
            .addBlockFile("${safename}-prologue.logic")
            .commit()
            .transaction()
            .executeFile("${safename}-delta.logic")

        if (options.SANITY.value) {
            lbScript.addBlockFile("${Doop.addonsPath}/sanity.logic")
        }

        if (options.ENABLE_REFLECTION.value) {
            def reflectionPath = "${Doop.analysesPath}/main/reflection"
            preprocess(this, "${outDir}/reflection-delta.logic", "${reflectionPath}/delta.logic")
            preprocess(this, "${outDir}/reflection-allocations-delta.logic", "${reflectionPath}/allocations-delta.logic")

            lbScript
                .commit()
                .transaction()
                .executeFile("reflection-delta.logic")
                .commit()
                .transaction()
                .executeFile("reflection-allocations-delta.logic")
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

        if (options.INFORMATION_FLOW.value) {
            preprocess(this, "${outDir}/information-flow-declarations.logic", "${Doop.addonsPath}/information-flow/declarations.logic")
            preprocess(this, "${outDir}/information-flow-delta.logic", "${Doop.addonsPath}/information-flow/delta.logic", macros)
            preprocess(this, "${outDir}/information-flow-rules.logic", "${Doop.addonsPath}/information-flow/rules.logic", macros)
            
            logger.info "Adding Information flow rules to addons logic"
            
            includeAtStart(this, "${outDir}/addons.logic", "${outDir}/information-flow-rules.logic")

            lbScript
                .addBlockFile("information-flow-declarations.logic")
                .commit()
                .transaction()
                .executeFile("information-flow-delta.logic")
                .commit()
                .transaction()
        }

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            preprocess(this, "${outDir}/dacapo-declarations.logic", "${Doop.addonsPath}/dacapo/declarations.logic")
            preprocess(this, "${outDir}/dacapo-delta.logic", "${Doop.addonsPath}/dacapo/delta.logic", macros)
            logger.info "Adding DaCapo rules to addons logic"
            includeAtStart(this, "${outDir}/addons.logic", "${Doop.addonsPath}/dacapo/rules.logic")

            lbScript
                .addBlockFile("dacapo-declarations.logic")
                .executeFile("dacapo-delta.logic")
        }

        if (options.TAMIFLEX.value) {
            preprocess(this, "${outDir}/tamiflex-declarations.logic", "${Doop.addonsPath}/tamiflex/declarations.logic")
            preprocess(this, "${outDir}/tamiflex-delta.logic", "${Doop.addonsPath}/tamiflex/delta.logic")
            logger.info "Adding tamiflex rules to addons logic"
            includeAtStart(this, "${outDir}/addons.logic", "${Doop.addonsPath}/tamiflex/rules.logic")

            lbScript
                .addBlockFile("tamiflex-declarations.logic")
                .executeFile("tamiflex-delta.logic")
        }

        includeAtStart(this, "${outDir}/${safename}.logic", "${outDir}/addons.logic")

        lbScript
            .commit()
            .elapsedTime()

        if (isRefineStep) importRefinement()

        lbScript
            .echo("-- Main Analysis --")
            .startTimer()
            .transaction()
            .addBlockFile("${safename}.logic")
            .commit()
            .elapsedTime()

        if (options.MUST.value) {
            preprocess(this, "${outDir}/must-point-to-may-pre-analysis.logic", "${Doop.analysesPath}/must-point-to/may-pre-analysis.logic")
            preprocess(this, "${outDir}/must-point-to.logic", "${Doop.analysesPath}/must-point-to/analysis-simple.logic")

            lbScript
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

    /**
     * Reanalyze.
     */
    protected void reanalyze() {
        preprocess(this, "${outDir}/refinement-delta.logic", "${Doop.analysesPath}/${name}/refinement-delta.logic")

        lbScript
            .echo("++++ Refinement ++++")
            .echo("-- Pre-Export --")
            .startTimer()
            .transaction()
            .executeFile("refinement-delta.logic")
            .commit()
            .elapsedTime()
            .echo("-- Export --")
            .startTimer()
            .transaction()
            .wr("export --keepSystemPreds --format script --dataDir . --overwrite --predicates TempSiteToRefine")
            .wr("export --keepSystemPreds --format script --dataDir . --overwrite --predicates TempNegativeSiteFilter")
            .wr("export --keepSystemPreds --format script --dataDir . --overwrite --predicates TempObjectToRefine")
            .wr("export --keepSystemPreds --format script --dataDir . --overwrite --predicates TempNegativeObjectFilter")
            .commit()
            .elapsedTime()

        isRefineStep = true
        initDatabase()
        basicAnalysis()
        mainAnalysis()
    }

    void importRefinement() {
        Map<String, String> files = [
                "refine-site": """\
                               option,delimiter,","
                               option,hasColumnNames,true
                               option,quotedValues,true
                               option,escapeQuotedValues,true

                               fromFile,"${outDir}/TempSiteToRefine.csv",CallGraphEdgeSource,CallGraphEdgeSource
                               toPredicate,SiteToRefine,CallGraphEdgeSource""".toString().stripIndent(),

                "negative-site": """\
                                 option,delimiter,","
                                 option,hasColumnNames,true

                                 fromFile,"${outDir}/TempNegativeSiteFilter.csv",string,string
                                 toPredicate,NegativeSiteFilter,string""".toString().stripIndent(),

                "refine-object": """\
                                 option,delimiter,","
                                 option,hasColumnNames,true
                                 option,quotedValues,true
                                 option,escapeQuotedValues,true

                                 fromFile,"${outDir}/TempObjectToRefine.csv",HeapAllocation,HeapAllocation
                                 toPredicate,ObjectToRefine,HeapAllocation""".toString().stripIndent(),

                "negative-object": """\
                                   option,delimiter,","
                                   option,hasColumnNames,true

                                   fromFile,"${outDir}/TempNegativeObjectFilter.csv",string,string
                                   toPredicate,NegativeObjectFilter,string""".toString().stripIndent()
        ]

        lbScript
            .echo("-- Import --")
            .startTimer()
            .transaction()

        files.each { Map.Entry<String, String> entry ->
            File f = new File(outDir, "${entry.key}.import")
            FileOps.writeToFile f, entry.value
            lbScript.wr("import --format script --importDir . --files ${f.getName()}")
        }

        lbScript
            .commit()
            .elapsedTime()
    }

    protected void runTransformInput() {
        preprocess(this, "${outDir}/transform.logic", "${Doop.addonsPath}/transform/rules.logic", "${Doop.addonsPath}/transform/declarations.logic")
        lbScript
            .echo("-- Transforming Facts --")
            .startTimer()
            .transaction()
            .addBlockFile("${outDir}/transform.logic")
            .commit()

        2.times { int i ->
            lbScript
                .echo(""" "-- Transformation (step $i) --" """)
                .transaction()
                .executeFile("${Doop.addonsPath}/transform/delta.logic")
                .commit()
        }
        lbScript.elapsedTime()
    }

    protected void produceStats() {
        if (options.X_STATS_NONE.value) return;

        if (options.X_STATS_AROUND.value) {
            lbScript.include(options.X_STATS_AROUND.value as String)
            return
        }

        def statsPath = "${Doop.addonsPath}/statistics"
        preprocess(this, "${outDir}/statistics-simple.logic", "${statsPath}/statistics-simple.logic")

        lbScript
            .echo("-- Statistics --")
            .startTimer()
            .transaction()
            .addBlockFile("statistics-simple.logic")

        if (options.X_STATS_FULL.value) {
            preprocess(this, "${outDir}/statistics.logic", "${statsPath}/statistics.logic")
            lbScript.addBlockFile("statistics.logic")
        }

        lbScript
            .commit()
            .elapsedTime()
    }

    protected void runJPhantom(){
        logger.info "-- Running jphantom to generate complement jar --"

        String jar = inputJarFiles[0].toString()
        String jarName = FilenameUtils.getBaseName(jar)
        String jarExt = FilenameUtils.getExtension(jar)
        String newJar = "${jarName}-complemented.${jarExt}"
        String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
        logger.debug "Params of jphantom: ${params.join(' ')}"

        //we invoke the main method reflectively to avoid adding jphantom as a compile-time dependency
        ClassLoader loader = phantomClassLoader()
        Helper.execJava(loader, "org.clyze.jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        inputJarFiles[0] = FileOps.findFileOrThrow("$outDir/$newJar", "jphantom invocation failed")
    }

    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)
    }

    protected void runSoot() {
        Collection<String> depArgs

        if (options.RUN_AVERROES.value) {
            //change linked arg and injar accordingly
            inputJarFiles[0] = FileOps.findFileOrThrow("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar".toString()]
        }
        else {
            Collection<String> deps = inputJarFiles.drop(1).collect{ File f -> ["-l", f.toString()]}.flatten() as Collection<String>
            if (jreJars.isEmpty()) {
                depArgs = ["-lsystem"] + deps
            }
            else {
                depArgs = jreJars.collect{ String arg -> ["-l", arg]}.flatten() + deps
            }

        }

        Collection<String> params = ["--allow-phantom", "--full", "--keep-line-number"] + depArgs + ["--application-regex", options.APP_REGEX.value.toString()]
        if (options.SSA.value) {
            params = params + ["--ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params = params + ["--allow-phantom"]
        }

        if (options.USE_ORIGINAL_NAMES.value) {
            params = params + ["--use-original-names"]
        }

        if (options.ONLY_APPLICATION_CLASSES_FACT_GEN.value) {
            params = params + ["--only-application-classes-fact-gen"]
        }

        params = params + ["-d", facts.toString(), inputJarFiles[0].toString()]

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
        Collection<String> libraryJars = inputJarFiles.drop(1).collect { it.toString() } + jreAverroesLibraries()

        //Create the averroes properties
        Properties props = new Properties()
        props.setProperty("application_includes", options.APP_REGEX.value as String)
        props.setProperty("main_class", options.MAIN_CLASS as String)
        props.setProperty("input_jar_files", inputJarFiles[0].toString())
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

        String jre = options.JRE.value
        String path = "${options.JRE_LIB.value}/jre${jre}/lib"
        //Not using if/else for readability
        switch(jre) {
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

        String jre = options.JRE.value

        if (jre == "system") {
            String javaHome = System.getProperty("java.home")
            return "$javaHome/lib/rt.jar"
        }
        else {
            String path = "${options.JRE_LIB.value}/jre${jre}/lib"
            return "$path/rt.jar"
        }
    }

    private void bloxbatch(File database, String params) {
        executor.execute("${options.BLOXBATCH.value} -db $database $params", IGNORED_WARNINGS)
    }

    private long timing(Closure c) {
        long now = System.currentTimeMillis()
        try {
            c.call()
        }
        catch(e) {
            throw e
        }
        //we measure the time only in error-free cases
        return ((System.currentTimeMillis() - now) / 1000).longValue()
    }
}
