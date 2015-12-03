package doop.core

import doop.blox.BloxbatchConnector
import doop.blox.BloxbatchScript
import doop.blox.WorkspaceConnector
import doop.input.InputResolutionContext
import doop.system.CppPreprocessor
import doop.system.Executor

import groovy.transform.TypeChecked

import java.util.regex.Pattern
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
 *
 * In general, the Analysis run() method implements the behavior of the original doop script.
 * For supporting invocations over the web, the behavior of the get-stats function of the original doop script is
 * broken into two parts: (a) produce statistics and (b) print statistics.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 */
@TypeChecked class Analysis implements Runnable {

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

    CppPreprocessor preprocessor = new CppPreprocessor()

    Executor executor

    File facts, cacheFacts, database, exportDir, averroesDir

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
        this.options = options
        this.ctx = ctx
        this.inputJarFiles = inputJarFiles
        this.jreJars = jreJars
        this.commandsEnvironment = commandsEnvironment

        executor = new Executor(commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }

        facts          = new File(outDir, "facts")
        cacheFacts     = new File(cacheDir)
        database       = new File(outDir, "database")
        exportDir      = new File(outDir, "export")
        averroesDir    = new File(outDir, "averroes")

        lbScript       = new BloxbatchScript(new File(outDir, "run.lb"))

        // Create workspace connector (needed by the post processor and the server-side analysis execution)
        connector = new BloxbatchConnector(database, commandsEnvironment)
    }

    @Override
    void run() {
        generateFacts()
        if (options.X_ONLY_FACTS.value)
            return

        initDatabase()

        analyze()

        try {
            File f = Helper.checkFileOrThrowException("${Doop.doopLogic}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
            logger.info "-- Re-Analyze --"
            reanalyze()
        }
        catch(e) {
            logger.debug e.getMessage()
        }

        if (!options.X_STATS_NONE.value)
            produceStats()

        lbScript.close()

        logger.info "Using generated script ${lbScript.getPath()}"
        logger.info "\nAnalysis START"
        long t = timing {
             def bloxOpts = options.BLOX_OPTS.value ?: ''
             executor.execute(outDir, "${options.BLOXBATCH.value} -script ${lbScript.getPath()} $bloxOpts", IGNORED_WARNINGS)
        }
        logger.info "Analysis END\n"
        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
        bloxbatchPipe database, """-execute '+Stats:Runtime("script wall-clock time (sec)", $t).
                                             +Stats:Runtime("disk footprint (KB)", $dbSize).'"""
    }


    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:name, outDir:outDir, cacheDir:cacheDir, inputs:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    protected void generateFacts() {

        FileUtils.deleteQuietly(facts)
        facts.mkdirs()

        if (cacheFacts.exists() && options.CACHE.value) {
            logger.info "Using cached facts from $cacheFacts"
            Helper.copyDirectoryContents(cacheFacts, facts)
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
            Helper.copyDirectoryContents(facts, cacheFacts)
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
        FileUtils.copyFile(new File("${Doop.doopLogic}/facts/declarations.logic"),
                           new File("${outDir}/facts-declarations.logic"))
        FileUtils.copyFile(new File("${Doop.doopLogic}/facts/flow-insensitivity-declarations.logic"),
                           new File("${outDir}/flow-insensitivity-declarations.logic"))
        FileUtils.copyFile(new File("${Doop.doopLogic}/facts/entities-import.logic"),
                           new File("${outDir}/entities-import.logic"))
        FileUtils.copyFile(new File("${Doop.doopLogic}/facts/import.logic"),
                           new File("${outDir}/facts-import.logic"))
        FileUtils.copyFile(new File("${Doop.doopLogic}/facts/flow-insensitivity-delta.logic"),
                           new File("${outDir}/flow-insensitivity-delta.logic"))

        lbScript
            .createDB(database.getName())
            .echo("-- Facts --")
            .startTimer()
            .transaction()
            .addBlockFile("facts-declarations.logic")
            .addBlockFile("flow-insensitivity-declarations.logic")
            .execute("""+Stats:Runtime("soot-fact-generation time (sec)", $sootTime).""")
            .executeFile("entities-import.logic")
            .executeFile("facts-import.logic")
            .executeFile("flow-insensitivity-delta.logic")

        if (options.TAMIFLEX.value) {
            String tamiflexDir = "${Doop.doopLogic}/addons/tamiflex"

            FileUtils.copyFile(new File("${tamiflexDir}/fact-declarations.logic"),
                               new File("${outDir}/tamiflex-fact-declarations.logic"))
            FileUtils.copyFile(new File("${tamiflexDir}/import.logic"),
                               new File("${outDir}/tamiflex-import.logic"))
            FileUtils.copyFile(new File("${tamiflexDir}/post-import.logic"),
                               new File("${outDir}/tamiflex-post-import.logic"))
            lbScript
                .addBlockFile("tamiflex-fact-declarations.logic") 
                .executeFile("tamiflex-import.logic")
                .addBlockFile("tamiflex-post-import.logic")
        }

        if (options.MAIN_CLASS.value)
            lbScript.execute("""+MainClass(x) <- ClassType(x), Type:fqn(x:"${options.MAIN_CLASS.value}").""")

        lbScript
            .commit()
            .elapsedTime()

        if (options.TRANSFORM_INPUT.value)
            runTransformInput()
    }

    /**
     * Performs the main part of the analysis.
     */
    protected void analyze() {
        lbScript
            .echo("-- Prologue --")
            .startTimer()
            .transaction()

        String analysisPath = null;
        String coreAnalysisName = null;

        if(isMustPointTo() && options.MAY_PRE_ANALYSIS.value) {
            String mayAnalysis = options.MAY_PRE_ANALYSIS.value
            analysisPath = "${Doop.doopLogic}/analyses/${mayAnalysis}"
            coreAnalysisName = mayAnalysis
        }
        else {
            analysisPath = "${Doop.doopLogic}/analyses/${name}"
            coreAnalysisName = name
        }

        if (options.DYNAMIC.value) {
            //TODO: Check arity of DYNAMIC file
            List<String> dynFiles = options.DYNAMIC.value as List<String>
            dynFiles.eachWithIndex { String dynFile, Integer index ->
                File f = new File(dynFile)
                FilenameUtils
                File dynImport = new File(outDir, "dynamic${index}.import")
                Helper.writeToFile dynImport, """\
                                              option,delimiter,"\t"
                                              option,hasColumnNames,false

                                              fromFile,"${f.getCanonicalPath()}",a,inv,b,type
                                              toPredicate,Config:DynamicClass,type,inv
                                              """.toString().stripIndent()

                lbScript.wr("import -f $dynImport")
            }
        }

        preprocessor.preprocess(this, analysisPath, "declarations.logic", "${outDir}/${coreAnalysisName}-declarations.logic")
        lbScript.addBlockFile("${coreAnalysisName}-declarations.logic")

        if (options.SANITY.value) {
            lbScript
                .echo("-- Loading Sanity Rules --")
                .addBlockFile("${Doop.doopLogic}/addons/sanity.logic")
        }

        preprocessor.preprocess(this, analysisPath, "delta.logic", "${outDir}/${coreAnalysisName}-delta.logic")
        lbScript.executeFile("${coreAnalysisName}-delta.logic")


        if (options.ENABLE_REFLECTION.value) {
            String reflectionPath = "${Doop.doopLogic}/core/reflection"

            preprocessor.preprocess(this, reflectionPath, "delta.logic", "${outDir}/reflection-delta.logic")
            lbScript
                .executeFile("reflection-delta.logic")
                .commit()
                .transaction()
                .executeFile("${reflectionPath}/allocations-delta.logic")
        }

        String addonsPath = "${Doop.doopLogic}/addons"
        String macros = "${Doop.doopLogic}/analyses/${coreAnalysisName}/macros.logic"
        /**
         * Generic file for incrementally adding addons logic from various
         * points. This is necessary in some cases to avoid weird errors from
         * the engine (DELTA_RECURSION etc.) and in general it helps
         * performance-wise.
         */ 
        File addons = new File(outDir, "addons.logic")
        FileUtils.deleteQuietly(addons)
        FileUtils.touch(addons)

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            FileUtils.copyFile(new File("${addonsPath}/dacapo/declarations.logic"),
                               new File("${outDir}/dacapo-declarations.logic"))
            preprocessor.preprocess(this, addonsPath, "dacapo/delta.logic", "${outDir}/dacapo-delta.logic", macros)
            lbScript
                .addBlockFile("dacapo-declarations.logic")
                .executeFile("dacapo-delta.logic")

            logger.info "Adding DaCapo rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "dacapo/rules.logic", "${outDir}/dacapo.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/dacapo.logic")
        }

        if (options.TAMIFLEX.value) {
            FileUtils.copyFile(new File("${addonsPath}/tamiflex/declarations.logic"),
                               new File("${outDir}/tamiflex-declarations.logic"))
            FileUtils.copyFile(new File("${addonsPath}/tamiflex/delta.logic"),
                               new File("${outDir}/tamiflex-delta.logic"))
            lbScript
                .addBlockFile("tamiflex-declarations.logic")
                .executeFile("tamiflex-delta.logic")

            logger.info "Adding tamiflex rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "tamiflex/rules.logic", "${outDir}/tamiflex.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/tamiflex.logic")
        }

        if (options.FU_EXCEPTION_FLOW.value) {
            preprocessor.preprocess(this, addonsPath, "fu-exception-flow/declarations.logic",
                                    "${outDir}/fu-exception-flow.logic",
                                    "fu-exception-flow/rules.logic")
            preprocessor.preprocess(this, addonsPath, "fu-exception-flow/delta.logic",
                                    "${outDir}/fu-exception-flow-delta.logic")
            lbScript
                .addBlockFile("${outDir}/fu-exception-flow.logic")
                .executeFile("${outDir}/fu-exception-flow-delta.logic")
        }

        if (options.AUXILIARY_HEAP.value) {
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/declarations.logic",
                                    "${outDir}/client-extensions.logic")
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/delta.logic",
                                    "${outDir}/client-extensions-delta.logic")
            lbScript
                .addBlockFile("${outDir}/client-extensions.logic")
                .executeFile("${outDir}/client-extensions-delta.logic")
        }

        if (options.REFINE.value)
            refine()

        if(isMustPointTo()) {
            String mustAnalysisPath = "${Doop.doopLogic}/analyses/${name}"

            if(options.MAY_PRE_ANALYSIS.value) {
                String mayAnalysis = options.MAY_PRE_ANALYSIS.value;
                analysisPath = mustAnalysisPath
                preprocessor.preprocess(this, analysisPath, "analysis.logic", "${outDir}/${coreAnalysisName}.logic")

                lbScript
                    .commit()
                    .transaction()
                    .addBlockFile("${coreAnalysisName}.logic")
                    .commit()
                    .transaction()
                    .addBlockFile("${mustAnalysisPath}/may-pre-analysis.logic")
            }

            lbScript
                // Default option for RootMethodForMustAnalysis.
                // TODO: add command line option, so users can provide their own subset of root methods
                .addBlock("RootMethodForMustAnalysis(?meth) <- MethodSignature:DeclaringType[?meth] = ?class, ApplicationClass(?class), Reachable(?meth).")
                //TODO: Default Root Methods for 'simple' must-analyses.
                .addBlockFile("${Doop.doopLogic}/addons/cfg-analysis/declarations.logic")
                .addBlockFile("${Doop.doopLogic}/addons/cfg-analysis/rules.logic")
        }

        preprocessor.preprocess(this, analysisPath, "analysis.logic", "${outDir}/${name}.logic")
        if(isMustPointTo()) 
            Helper.appendAtFirst(this, "${outDir}/${coreAnalysisName}.logic", "${outDir}/addons.logic")
        else
            Helper.appendAtFirst(this, "${outDir}/${name}.logic", "${outDir}/addons.logic")

        lbScript
            .commit()
            .elapsedTime()
            .echo("-- Analysis --")
            .startTimer()
            .transaction()
            .addBlockFile("${name}.logic")
            .commit()
            .elapsedTime()
    }

    /**
     * Reanalyze.
     */
    protected void reanalyze() {
        logger.info "Loading ${name} refinement-delta rules"

        preprocessor.preprocess(this, "${Doop.doopLogic}/${name}", "refinement-delta.logic", "${outDir}/${name}-refinement-delta.logic")
        // TODO: handle exportCsv in script
        timing {
            bloxbatchPipe database, "-execute -file ${outDir}/${name}-refinement-delta.logic"
        }

        timing {
            bloxbatchPipe database, "-exportCsv TempSiteToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatchPipe database, "-exportCsv TempNegativeSiteFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatchPipe database, "-exportCsv TempObjectToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatchPipe database, "-exportCsv TempNegativeObjectFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        generateFacts()
        initDatabase()
        //TODO: We don't need to write-meta, do we?
        options.REFINE.value = true
        analyze()
    }

    protected void refine() {

        //The files and their contents
        Map<String, String> files = [
                "refine-site": """\
                               option,delimiter,","
                               option,hasColumnNames,false
                               option,quotedValues,true
                               option,escapeQuotedValues,true

                               fromFile,"${outDir}/${name}-TempSiteToRefine.csv",CallGraphEdgeSource,CallGraphEdgeSource
                               toPredicate,SiteToRefine,CallGraphEdgeSource""".toString().stripIndent(),

                "negative-site": """\
                                 option,delimiter,","
                                 option,hasColumnNames,false

                                 fromFile,"${outDir}/${name}-TempNegativeSiteFilter.csv",string,string
                                 toPredicate,NegativeSiteFilter,string""".toString().stripIndent(),

                "refine-object": """\
                                 option,delimiter,","
                                 option,hasColumnNames,false
                                 option,quotedValues,true
                                 option,escapeQuotedValues,true

                                 fromFile,"${outDir}/${name}-TempObjectToRefine.csv",HeapAllocation,HeapAllocation
                                 toPredicate,ObjectToRefine,HeapAllocation""".toString().stripIndent(),

                "negative-object": """\
                                   option,delimiter,","
                                   option,hasColumnNames,false

                                   fromFile,"${outDir}/${name}-TempNegativeObjectFilter.csv",string,string
                                   toPredicate,NegativeObjectFilter,string""".toString().stripIndent()
        ]

        logger.info "loading $name refinement facts "
        files.each { Map.Entry<String, String> entry ->
            File f = new File(outDir, "${name}-${entry.key}.import")
            Helper.writeToFile f, entry.value
            Helper.checkFileOrThrowException(f, "Could not create import file: $f")
            lbScript.wr("import -f $f")
        }
    }

    protected void runTransformInput() {
        preprocessor.preprocess(this, "${Doop.doopLogic}/addons/transform", "rules.logic", "${outDir}/transform.logic", "${Doop.doopLogic}/addons/transform/declarations.logic")
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
                .executeFile("${Doop.doopLogic}/addons/transform/delta.logic")
                .commit()
        }
        lbScript.elapsedTime()
    }

    protected void produceStats() {
        String statsPath = "${Doop.doopLogic}/addons/statistics"
        preprocessor.preprocess(this, statsPath, "statistics-simple.logic", "${outDir}/statistics-simple.logic")
        preprocessor.preprocess(this, statsPath, "delta.logic", "${outDir}/statistics-delta.logic")

        lbScript
            .echo("-- Statistics --")
            .startTimer()
            .transaction()
            .addBlockFile("statistics-simple.logic")

        if (options.X_STATS_FULL.value) {
            preprocessor.preprocess(this, statsPath, "statistics.logic", "${outDir}/statistics.logic")
            lbScript.addBlockFile("statistics.logic")
        }

        lbScript
            .commit()
            // Need to be in a separate transaction, since IDB and delta rules shouldn't be together
            .transaction()
            .executeFile("statistics-delta.logic")
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
        Helper.execJava(loader, "jphantom.Driver", params)

        //set the jar of the analysis to the complemented one
        File f = Helper.checkFileOrThrowException("$outDir/$newJar", "jphantom invocation failed")
        inputJarFiles[0] = f
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
            inputJarFiles[0] = Helper.checkFileOrThrowException("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
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

        Collection<String> params = ["-full", "-keep-line-number"] + depArgs + ["-application-regex", options.APP_REGEX.value.toString()]

        if (options.SSA.value) {
            params = params + ["-ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params = params + ["-allow-phantom"]
        }

        if (options.USE_ORIGINAL_NAMES.value) {
            params = params + ["-use-original-names"]
        }

        if (options.ONLY_APPLICATION_CLASSES_FACT_GEN.value) {
            params = params + ["-only-application-classes-fact-gen"]
        }

        params = params + ["-d", facts.toString(), inputJarFiles[0].toString()]

        logger.debug "Params of soot: ${params.join(' ')}"

        sootTime = timing {
            //We invoke soot reflectively to be able to specify the soot-classes jar at runtime
            ClassLoader loader = sootClassLoader()
            Helper.execJava(loader, "doop.soot.Main", params.toArray(new String[params.size()]))
        }
    }

    protected boolean isMustPointTo() {
        return Helper.isMustPointTo(name)
    }

    /**
     * Creates a new class loader for running jphantom
     */
    private ClassLoader phantomClassLoader() {
        //TODO: for now, we hard-code the jphantom jar
        String jphantom = "${Doop.doopHome}/lib/jphantom-1.1-jar-with-dependencies.jar"
        File f = Helper.checkFileOrThrowException(jphantom, "jphantom jar missing or invalid: $jphantom")
        List<URL> classpath = [f.toURI().toURL()]
        return new URLClassLoader(classpath as URL[])
    }

    /**
     * Creates a new class loader for running soot
     */
    private ClassLoader sootClassLoader() {
        String sootClasses = "${Doop.doopHome}/lib/sootclasses-${options.X_SOOT_VERSION.value}.jar"
        File f1 = Helper.checkFileOrThrowException(sootClasses, "soot classes jar missing or invalid: $sootClasses")
        List<URL> classpath = [f1.toURI().toURL()]
        return new URLClassLoader(classpath as URL[], ClassLoader.getSystemClassLoader())
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
        
        File f1 = Helper.checkFileOrThrowException(jar, "averroes jar missing or invalid: $jar")
        File f2 = Helper.checkFileOrThrowException(properties, "averroes properties missing or invalid: $properties")
        
        List<URL> classpath = [f1.toURI().toURL(), f2.toURI().toURL()]
        return new URLClassLoader(classpath as URL[])
    }
    
    /**
     * Generates a list for the jre libs for averroes 
     */
    private List<String> jreAverroesLibraries() {

        String jre = options.JRE.value
        String path = "${options.EXTERNALS.value}/jre${jre}/lib"
        //Not using if/else for readability
        switch(jre) {
            case "1.3":
                return []
            case "1.4":
                return ["${path}/jce.jar", "${path}/jsse.jar"]
            case "1.5":
                return ["${path}/jce.jar", "${path}/jsse.jar"]
            case "1.6":
                return ["${path}/jce.jar", "${path}/jsse.jar"]
            case "1.7":
                return ["${path}/jce.jar", "${path}/jsse.jar"]
            case "system":
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
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
            String path = "${options.EXTERNALS.value}/jre${jre}/lib"
            return "$path/rt.jar"
        }
    }

    /**
     * Invokes bloxbatch on the given database with the given params, piping it up with supplied pipeCommands.
     */
    private void bloxbatchPipe(File database, String params, String... pipeCommands) {
        String command = "${options.BLOXBATCH.value} -db $database $params"
        if (pipeCommands)
            command += " | ${pipeCommands.join(" |")}"

        executor.execute(command, IGNORED_WARNINGS)
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
