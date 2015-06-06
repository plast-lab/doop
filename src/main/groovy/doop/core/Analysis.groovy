package doop.core

import doop.blox.BloxbatchConnector
import doop.blox.WorkspaceConnector
import doop.input.InputResolutionContext
import doop.system.CppPreprocessor
import doop.system.Executor

import groovy.transform.TypeChecked
import groovy.ui.SystemOutputInterceptor
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
     * The output dir of the analysis
     */
    String outDir

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
    List<File> jars

    /**
     * The environment for running external commands
     */
    Map<String, String> commandsEnvironment

    CppPreprocessor preprocessor = new CppPreprocessor()

    Executor executor

    File facts, cacheFacts, database, exportDir, averroesDir, lbScript

    Writer lbScriptWriter

    long sootTime

    private static final List<String> IGNORED_WARNINGS = [
        """\
        *******************************************************************
        Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.
        Please use 'lb' instead of 'bloxbatch'.
        *******************************************************************
        """
    ].collect{ line -> Pattern.quote(line.stripIndent()) }

    protected Analysis() {}

    private void Init() {
        executor = new Executor(commandsEnvironment)

        new File(outDir, "meta").withWriter { BufferedWriter w -> w.write(this.toString()) }

        facts         = new File(outDir, "facts")
        cacheFacts    = new File(outDir, "cacheFacts")
        database      = new File(outDir, "database")
        exportDir     = new File(outDir, "export")
        averroesDir   = new File(outDir, "averroes")

        lbScript      = new File(outDir, "run.lb")
        lbScriptWriter = new PrintWriter(lbScript)
    }

    @Override
    void run() {
        Init()

        generateFacts()

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

        if (!options.NO_STATS.value)
            produceStats()

        lbScriptWriter.close()

        logger.info "Running generated script $lbScript"
        long t = timing {
            executor.execute("cd $outDir ; ${options.BLOXBATCH.value} -script $lbScript", IGNORED_WARNINGS)
        }
        bloxbatchPipe database, """-execute '+Stats:Runtime("script wall-clock time (sec)", $t).'"""
        int dbSize = (FileUtils.sizeOfDirectory(database) / 1024).intValue()
        bloxbatchPipe database, """-execute '+Stats:Runtime("disk footprint (KB)", $dbSize).'"""
    }

    void printStats() {
        // Create workspace connector
        WorkspaceConnector connector = new BloxbatchConnector(database)
        connector.environment = commandsEnvironment

        // We have to store the query results to a list since the
        // closure argument of the connector does not generate an
        // iterable stream.
        // 
        // TODO: change the connector so that it produces an iterable

        def lines = [] as List<String>
        connector.processPredicate("Stats:Runtime") { String line ->
            lines.add(line)
        }

        logger.info "-- Runtime metrics --"
        lines.sort()*.split(", ").each {
            printf("%-80s %,.2f\n", it[0], it[1] as float)
        }

        if (!options.NO_STATS.value) {
            lines = [] as List<String>
            connector.processPredicate("Stats:Metrics") { String line ->
                lines.add(line)
            }

            // We have to first sort (numerically) by the 1st column and
            // then erase it

            logger.info "-- Statistics --"
            lines.sort()*.replaceFirst(/^[0-9]+[ab]?@ /, "")*.split(", ").each {
                printf("%-80s %,d\n", it[0], it[1] as int)
            }
        }
    }

    void linkResult() {
        def jre = options.JRE.value
        if (jre != "system") jre = "jre${jre}"
        def jarName = FilenameUtils.getBaseName(jars[0].toString())

        def humanDatabase = new File("${Doop.doopHome}/results/${jarName}/${name}/${jre}/${id}")
        humanDatabase.mkdirs()
        logger.info "Making database available at $humanDatabase"
        executor.execute("ln -s -f $database $humanDatabase")

        def lastAnalysis = "${Doop.doopHome}/last-analysis"
        logger.info "Making database available at $lastAnalysis"
        executor.execute("ln -s -f -n $database $lastAnalysis")
    }

    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:name, outDir:outDir, inputs:ctx.toString()].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    protected void generateFacts() {

        if (cacheFacts.exists() && options.CACHE.value) {
            logger.info "Using cached facts $cacheFacts"
        }
        else if (options.CSV.value) {
            cacheFacts.mkdirs()
            Helper.moveDirectoryContents(exportDir, cacheFacts)
        }
        else {
            logger.info "-- Fact Generation --"

            FileUtils.deleteQuietly(facts)
            facts.mkdirs()

            if (options.RUN_JPHANTOM.value) {
                runJPhantom()
            }

            if (options.AVERROES.value) {
                runAverroes()
            }

            runSoot()

            FileUtils.touch(new File(facts, "ApplicationClass.facts"))
            FileUtils.touch(new File(facts, "Properties.facts"))

            if (options.TAMIFLEX.value) {
                File origTamFile  = new File(options.TAMIFLEX.value.toString())
                File factsTamFile = new File(facts, "Tamiflex.facts")

                factsTamFile.withWriter { w ->
                    origTamFile.eachLine { line ->
                        w << line
                                .replaceFirst(/;[^;]*;$/, "")
                                .replaceFirst(/;$/, ";0")
                                .replaceFirst(/(^.*;.*)\.([^.]+;[0-9]+$)/) { full, first, second -> first+";"+second+"\n" }
                    }
                }
            }

            FileUtils.deleteQuietly(cacheFacts)
            cacheFacts.mkdirs()
            Helper.copyDirectoryContents(facts, cacheFacts)
        }
    }

    protected void initDatabase() {

        lbScriptWriter.println('echo "-- Database Initialization --"')

        if (options.INCREMENTAL.value) {
            File libDatabase = Helper.checkDirectoryOrThrowException("$outDir/libdb", "Preanalyzed library database is missing!")
            logger.info "Copying precomputed database of library from $libDatabase"
            timing {
                Helper.copyDirectoryContents(libDatabase, database)
            }
        }
        else {
            FileUtils.deleteQuietly(database)

            lbScriptWriter.println("create $database --overwrite --blocks base")

            lbScriptWriter.println("startTimer")
            lbScriptWriter.println("transaction")
            lbScriptWriter.println("addBlock -F ${Doop.doopLogic}/facts/declarations.logic -B FactDecls")
            lbScriptWriter.println("addBlock -F ${Doop.doopLogic}/facts/flow-insensitivity-declarations.logic")
            lbScriptWriter.println("""exec '+Stats:Runtime("soot-fact-generation time (sec)", $sootTime).'""")

            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/entities-import.logic"),
                               new File("${outDir}/entities-import.logic"))
            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/import.logic"),
                               new File("${outDir}/facts-import.logic"))
            lbScriptWriter.println("exec -F entities-import.logic")
            lbScriptWriter.println("exec -F facts-import.logic")

            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/flow-insensitivity-delta.logic"),
                               new File("${outDir}/flow-insensitivity-delta.logic"))
            lbScriptWriter.println("exec -F flow-insensitivity-delta.logic")

            if (options.TAMIFLEX.value) {
                String tamiflexDir = "${Doop.doopLogic}/addons/tamiflex"

                lbScriptWriter.println("addBlock -F ${tamiflexDir}/fact-declarations.logic -B TamiflexFactDecls")

                FileUtils.copyFile(new File("${tamiflexDir}/import.logic"),
                                   new File("${outDir}/tamiflex-import.logic"))
                lbScriptWriter.println("exec -F tamiflex-import.logic")
                lbScriptWriter.println("addBlock -F ${tamiflexDir}/post-import.logic")
            }

            if (options.MAIN_CLASS.value) {
                lbScriptWriter.println("""exec '+MainClass(x) <- ClassType(x), Type:fqn(x:"${options.MAIN_CLASS.value}").'""")
            }

            lbScriptWriter.println("commit")
            lbScriptWriter.println("elapsedTime")

            if (options.SET_BASED.value) {
                runSetBased()
            }
        }
    }

    /**
     * Performs the main part of the analysis.
     */
    protected void analyze() {

        lbScriptWriter.println('echo "-- Analysis Prologue --"')
        lbScriptWriter.println("startTimer")
        lbScriptWriter.println("transaction")

        String analysisPath = "${Doop.doopLogic}/analyses/${name}"

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

                lbScriptWriter.println("import -f $dynImport")
            }
        }

        if (!options.INCREMENTAL.value) {
            preprocessor.preprocess(this, analysisPath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            lbScriptWriter.println("addBlock -F ${name}-declarations.logic")

            if (options.SANITY.value) {
                lbScriptWriter.println('echo "-- Loading Sanity Rules --"')
                lbScriptWriter.println("addBlock -F ${Doop.doopLogic}/addons/sanity.logic")
            }
        }

        preprocessor.preprocess(this, analysisPath, "delta.logic", "${outDir}/${name}-delta.logic")
        lbScriptWriter.println("exec -F ${name}-delta.logic")

        if (!options.DISABLE_REFLECTION.value) {
            String reflectionPath = "${Doop.doopLogic}/core/reflection"

            preprocessor.preprocess(this, reflectionPath, "delta.logic", "${outDir}/reflection-delta.logic")
            lbScriptWriter.println("exec -F reflection-delta.logic")
            lbScriptWriter.println("commit")
            lbScriptWriter.println("transaction")
            lbScriptWriter.println("exec -F ${reflectionPath}/allocations-delta.logic")
        }

        String addonsPath = "${Doop.doopLogic}/addons"
        String macros = "${Doop.doopLogic}/analyses/${name}/macros.logic"
        /**
         * Generic file for incrementaly adding addons logic from various
         * points. This is necessary in some cases to avoid weird errors from
         * the engine (DELTA_RECURSION etc.) and in general it helps
         * performance-wise.
         */ 
        FileUtils.touch(new File(outDir, "addons.logic"))

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            lbScriptWriter.println("addBlock -F ${addonsPath}/dacapo/declarations.logic -B DacapoDecls")

            preprocessor.preprocess(this, addonsPath, "dacapo/delta.logic", "${outDir}/dacapo-delta.logic", macros)
            lbScriptWriter.println("exec -F dacapo-delta.logic")

            logger.info "Adding DaCapo rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "dacapo/rules.logic", "${outDir}/dacapo.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/dacapo.logic")
        }

        if (options.TAMIFLEX.value) {
            lbScriptWriter.println("addBlock -F ${addonsPath}/tamiflex/declarations.logic -B TamiflexDecls")
            lbScriptWriter.println("exec -F ${addonsPath}/tamiflex/delta.logic")

            logger.info "Adding tamiflex rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "tamiflex/rules.logic", "${outDir}/tamiflex.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/tamiflex.logic")
        }

        if (options.CLIENT_EXCEPTION_FLOW.value) {
            preprocessor.preprocess(this, addonsPath, "exception-flow/declarations.logic",
                                    "${outDir}/exception-flow.logic",
                                    "exception-flow/rules.logic")
            lbScriptWriter.println("addBlock -F ${outDir}/exception-flow.logic")

            preprocessor.preprocess(this, addonsPath, "exception-flow/delta.logic",
                                    "${outDir}/exception-flow-delta.logic")
            lbScriptWriter.println("exec -F ${outDir}/exception-flow-delta.logic")
        }

        if (options.CLIENT_EXTENSIONS.value) {
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/declarations.logic",
                                    "${outDir}/client-extensions.logic")
            lbScriptWriter.println("addBlock -F ${outDir}/client-extensions.logic")

            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/delta.logic",
                                    "${outDir}/client-extensions-delta.logic")
            lbScriptWriter.println("exec -F ${outDir}/client-extensions-delta.logic")
        }

        if (options.REFINE.value) {
            refine()
        }

        if (!options.INCREMENTAL.value) {

            String bloxOpts = options.BLOX_OPTS.value ?: ''

            if(isMustPointTo()) {
                if(options.MAY_PRE_ANALYSIS.value) {
                    String mayAnalysis = options.MAY_PRE_ANALYSIS.value;
                    
                    logger.info "Adding ${mayAnalysis} block"
                    lbScriptWriter.println("addBlock -F ${mayAnalysis}.logic $bloxOpts")

                    logger.info "Adding may-related logic for ${name}"
                    lbScriptWriter.println("addBlock -F may-pre-analysis.logic $bloxOpts")

                    // Default option for RootMethodForMustAnalysis.
                    // TODO: add command line option, so users can provide their own subset of root methods
                    logger.info "Adding block for RootMethodForMustAnalysis (default)"
                    lbScriptWriter.println("addBlock 'RootMethodForMustAnalysis(?meth) <- DeclaringClass:Method[?meth] = ?class, ApplicationClass(?class), Reachable(?meth).'")
                }

                //TODO: Default Root Methods for 'simple' must-analyses.
                lbScriptWriter.println('echo "-- CFG Analysis Block --"')
                lbScriptWriter.println("addBlock -F ${Doop.doopLogic}/addons/cfg-analysis/declarations.logic")
                lbScriptWriter.println("addBlock -F ${Doop.doopLogic}/addons/cfg-analysis/rules.logic")
            }

            preprocessor.preprocess(this, analysisPath, "analysis.logic", "${outDir}/${name}.logic")
            Helper.appendAtFirst(this, "${outDir}/${name}.logic", "${outDir}/addons.logic")

            lbScriptWriter.println("commit")
            lbScriptWriter.println("elapsedTime")
            lbScriptWriter.println('echo "-- Main Analysis --"')
            lbScriptWriter.println("startTimer")
            lbScriptWriter.println("transaction")
            lbScriptWriter.println("addBlock -F ${name}.logic $bloxOpts")
            lbScriptWriter.println("commit")
            lbScriptWriter.println("elapsedTime")
        }
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
            lbScriptWriter.println("import -f $f")
        }
    }

    protected void runSetBased() {
        preprocessor.preprocess(this, "${Doop.doopLogic}/addons/transform", "rules.logic", "${outDir}/transform.logic", "${Doop.doopLogic}/addons/transform/declarations.logic")
        lbScriptWriter.println('echo "-- Transforming Input Facts --"')
        lbScriptWriter.println("startTimer")
        lbScriptWriter.println("transaction")
        lbScriptWriter.println("addBlock -F ${outDir}/transform.logic")
        lbScriptWriter.println("commit")

        2.times { int i ->
            lbScriptWriter.println("""echo "-- Transformation (step $i) --" """)
            lbScriptWriter.println("transaction")
            lbScriptWriter.println("exec -F ${Doop.doopLogic}/addons/transform/delta.logic")
            lbScriptWriter.println("commit")
        }
        lbScriptWriter.println("elapsedTime")
    }

    protected void produceStats() {
        String statsPath = "${Doop.doopLogic}/addons/statistics"

        lbScriptWriter.println('echo "-- Producing Statistics --"')
        lbScriptWriter.println("startTimer")

        lbScriptWriter.println("transaction")
        preprocessor.preprocess(this, statsPath, "statistics-simple.logic", "${outDir}/statistics-simple.logic")
        lbScriptWriter.println("addBlock -F statistics-simple.logic")

        if (options.FULL_STATS.value) {
            preprocessor.preprocess(this, statsPath, "statistics.logic", "${outDir}/statistics.logic")
            lbScriptWriter.println("addBlock -F statistics.logic")
        }
        lbScriptWriter.println("commit")

        // Need to be in a separate transaction, since IDB and delta rules shouldn't be together
        lbScriptWriter.println("transaction")
        preprocessor.preprocess(this, statsPath, "delta.logic", "${outDir}/statistics-delta.logic")
        lbScriptWriter.println("exec -F statistics-delta.logic")
        lbScriptWriter.println("commit")

        lbScriptWriter.println("elapsedTime")
    }

    protected void runJPhantom(){
        logger.info "-- Running jphantom to generate complement jar --"

        String jar = jars[0].toString()
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
        jars[0] = f
    }
    
    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)

        //We change linked arg and injar for soot in the runSoot method
    }

    protected void runSoot() {
        Collection<String> depArgs

        if (options.AVERROES.value) {
            //change linked arg and injar accordingly
            jars[0] = Helper.checkFileOrThrowException("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar".toString()]
        }
        else {
            Collection<String> deps = jars.drop(1).collect{ File f -> ["-l", f.toString()]}.flatten() as Collection<String>
            List<String> links = jreLinkArgs()
            if (links.isEmpty()) {
                depArgs = ["-lsystem"] + deps 
            }
            else {
                depArgs = links.collect{ String arg -> ["-l", arg]}.flatten() + deps
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

        if (options.MAIN_CLASS.value) {
            params = params + ["-main", options.MAIN_CLASS.value.toString()]
        }

        params = params + ["-d", facts.toString(), jars[0].toString()]

        logger.debug "Params of soot: ${params.join(' ')}"

        sootTime = timing {
            doop.soot.Main.main(params.toArray(new String[params.size()]))
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
        //TODO: for now, we hard-code the soot jars
        String sootClasses = "${Doop.doopHome}/lib/sootclasses-2.5.0.jar"
        String sootFactGeneration = "${Doop.doopHome}/lib/soot-fact-generation.jar"

        File f1 = Helper.checkFileOrThrowException(sootClasses, "soot classes jar missing or invalid: $sootClasses")
        File f2 = Helper.checkFileOrThrowException(sootFactGeneration, "soot fact generation jar missing or invalid: $sootFactGeneration")

    List<URL> classpath = [f1.toURI().toURL(), f2.toURI().toURL()]
    return new URLClassLoader(classpath as URL[])
    }

    /**
     * Generates a list of the jre link arguments for soot
     */
    private List<String> jreLinkArgs() {

        String jre = options.JRE.value
        String path = "${Doop.doopHome}/externals/jre${jre}/lib"

        switch(jre) {
            case "1.3":
                return Helper.checkFiles(["${path}/rt.jar".toString()])
            case "1.4":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.5":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.6":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString()])
            case "1.7":
                return Helper.checkFiles(["${path}/rt.jar".toString(),
                                          "${path}/jce.jar".toString(),
                                          "${path}/jsse.jar".toString(),
                                          "${path}/rhino.jar".toString()])
            case "system":
                /*
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/rt.jar", "$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
                */
                return []
        }
    }
    
    /**
     * Creates a new class loader for running averroes
     */
    private ClassLoader averroesClassLoader() {
        //TODO: for now, we hard-code the averroes jar and properties
        String jar = "${Doop.doopHome}/lib/averroes-no-properties.jar"
        String properties = "$outDir/averroes.properties"

        //Determine the library jars
        Collection<String> libraryJars = jars.drop(1).collect { it.toString() } + jreAverroesLibraries()
        
        //Create the averroes properties
        Properties props = new Properties()
        props.setProperty("application_includes", options.APP_REGEX.value as String)
        props.setProperty("main_class", options.MAIN_CLASS as String)
        props.setProperty("input_jar_files", jars[0].toString() as String)
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
        String path = "${Doop.doopHome}/externals/jre${jre}/lib"
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
            String path = "${Doop.doopHome}/externals/jre${jre}/lib"
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
