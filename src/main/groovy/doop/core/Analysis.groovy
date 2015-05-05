package doop.core

import doop.preprocess.Preprocessor
import doop.resolve.Dependency
import doop.resolve.ResolvedDependency
import groovy.ui.SystemOutputInterceptor
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
     * The output dir of the analysis
     */
    String outDir

    /**
     * The options of the analysis
     */
    Map<String, AnalysisOption> options

    /**
     * The jar files/dependencies of the analysis
     */
    List<Dependency> jars

    /**
     * The environment for running external commands
     */
    Map<String, String> commandsEnvironment

    /**
     * The preprocessor for the logic files of the analysis
     */
    Preprocessor preprocessor

    String inputFilesChecksum
    String logicFilesChecksum
    
    File cacheFacts, database, exportDir, facts, averroesDir

    long sootTime, factsTime

    protected Analysis() {}

    String bloxbatchVersion
    boolean hasFilter

    Writer lbScript

    @Override
    void run() {
        String scriptPath = "${outDir}/run.lb"
        lbScript = new PrintWriter(new File(scriptPath))

        //TODO: can these be in the constructor? problem with outDir not having a value yet
        new File(outDir, "meta").withWriter { Writer w -> w.write(this.toString()) }
        preprocessor.init()
        //TODO: We don't need to calculate logic and input sums, do we?
        //TODO: We don't need to annotate db paths, do we?
        facts         = new File(outDir, "facts")
        cacheFacts    = new File(outDir, "cacheFacts")
        database      = new File(outDir, "database")
        exportDir     = new File(outDir, "export")
        averroesDir   = new File(outDir, "averroes")
        
        createDatabase()

        analyze()

        try {
            File f = Helper.checkFileOrThrowException("${Doop.doopLogic}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
            logger.info "-- Re-Analyze --"
            reanalyze()
        }
        catch(e) {
            logger.debug e.getMessage()
        }

        produceStats()

        lbScript.close()

        logger.info "Running generated script ($scriptPath)"
        long t = timing {
            bloxbatchPipe database, "-script $scriptPath"
        }
        bloxbatchPipe database, """-execute '+Stats:Runtime("script wall-clock time (sec)", $t).'"""
        int dbSize = FileUtils.sizeOfDirectory(database) / 1024
        bloxbatchPipe database, """-execute '+Stats:Runtime("disk footprint (KB)", $dbSize).'"""
    }

    void printStats() {
        logger.info "-- Runtime metrics --"
        bloxbatchPipe database, "-query Stats:Runtime",
                                "sort -n",
                                "sed -r 's/^ +([0-9]+[ab]?@ )?//'",
                                "awk -F ', ' '{ printf(\"%-80s %'\\''.2f\\n\", \$1, \$2) }'"

        logger.info "-- Statistics --"
        bloxbatchPipe database, "-query Stats:Metrics",
                                "sort -n",
                                "sed -r 's/^ +[0-9]+[ab]?@ //'",
                                "awk -F ', ' '{ printf(\"%-80s %'\\''d\\n\", \$1, \$2) }'"
    }

    void linkResult() {
        String jre = options.JRE.value
        if (jre != "system") jre = "jre${jre}"
        String jarName = FilenameUtils.getBaseName(jars[0].resolve().toString())

        File humanDatabase = new File("${Doop.doopHome}/results/${jarName}/${name}/${jre}/${id}")
        logger.info "Making database available at $humanDatabase"
        humanDatabase.mkdirs()
        FileUtils.deleteQuietly(humanDatabase)
        Helper.execCommand("ln -s $database $humanDatabase", commandsEnvironment)

        logger.info "Making database available at last-analysis"
        File lastAnalysis = new File("${Doop.doopHome}/last-analysis")
        FileUtils.deleteQuietly(lastAnalysis)
        Helper.execCommand("ln -s $database $lastAnalysis", commandsEnvironment)
    }

    /**
     * @return A string representation of the analysis
     */
    String toString() {
        return [id:id, name:name, outDir:outDir].collect { Map.Entry entry -> "${entry.key}=${entry.value}" }.join("\n") +
               "\n" +
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n") + "\n"
    }

    /**
     * Executes a bloxbatch query, providing each line of output to the given closure as a String.
     */
    void query(String query, Closure closure) {
        Process process = Helper.startExternalProcess "${options.BLOXBATCH.value} -db $database -query $query",
                          commandsEnvironment,
                          true

        def runnable = [
            run: {
                process.in.withReader { Reader r ->
                    r.eachLine closure
                }
            }
        ] as Runnable

        Thread t = new Thread(runnable)
        t.start()

        t.join()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new RuntimeException("Execution of query returned a non-zero status:\n $query")
        }

        process.closeStreams()
    }

    protected void createDatabase() {

        if (cacheFacts.exists() && options.CACHE.value) {
            logger.info "Using cached facts $cacheFacts"
        }
        else if (options.CSV.value) {
            cacheFacts.mkdirs()
            Helper.moveDirectoryContents(exportDir, cacheFacts)
        }
        else {
            logger.info "Generating facts in $facts"

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
                File origTamFile  = new File(options.TAMIFLEX.value)
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

        initDatabase()
    }

    protected void initDatabase() {

        lbScript.println('echo "-- Database Initialization --"')

        if (options.INCREMENTAL.value) {
            File libDatabase = Helper.checkDirectoryOrThrowException("$outDir/libdb", "Preanalyzed library database is missing!")
            logger.info "Copying precomputed database of library from $libDatabase"
            timing {
                Helper.copyDirectoryContents(libDatabase, database)
            }
        }
        else {
            lbScript.println("create $database --overwrite --blocks base")

            lbScript.println("startTimer")
            lbScript.println("transaction")
            lbScript.println("addBlock -F ${Doop.doopLogic}/facts/declarations.logic -B FactDecls")
            lbScript.println("addBlock -F ${Doop.doopLogic}/facts/flow-insensitivity-declarations.logic")
            lbScript.println("""exec '+Stats:Runtime("soot-fact-generation time (sec)", $sootTime).'""")

            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/entities-import.logic"),
                               new File("${outDir}/entities-import.logic"))
            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/import.logic"),
                               new File("${outDir}/facts-import.logic"))
            lbScript.println("exec -F entities-import.logic")
            lbScript.println("exec -F facts-import.logic")

            FileUtils.copyFile(new File("${Doop.doopLogic}/facts/flow-insensitivity-delta.logic"),
                               new File("${outDir}/flow-insensitivity-delta.logic"))
            lbScript.println("exec -F flow-insensitivity-delta.logic")

            if (options.TAMIFLEX.value) {
                String tamiflexDir = "${Doop.doopLogic}/addons/tamiflex"

                lbScript.println("addBlock -F ${tamiflexDir}/fact-declarations.logic -B TamiflexFactDecls")

                FileUtils.copyFile(new File("${tamiflexDir}/import.logic"),
                                   new File("${outDir}/tamiflex-import.logic"))
                lbScript.println("exec -F tamiflex-import.logic")
                lbScript.println("addBlock -F ${tamiflexDir}/post-import.logic")
            }

            if (options.MAIN_CLASS.value) {
                lbScript.println("""exec '+MainClass(x) <- ClassType(x), Type:fqn(x:"${options.MAIN_CLASS.value}").'""")
            }

            lbScript.println("commit")
            lbScript.println("elapsedTime")

            if (options.SET_BASED.value) {
                runSetBased()
            }
        }
    }

    /**
     * Performs the main part of the analysis.
     */
    protected void analyze() {

        lbScript.println('echo "-- Analysis Prologue --"')
        lbScript.println("startTimer")
        lbScript.println("transaction")

        String analysisPath = "${Doop.doopLogic}/analyses/${name}"

        if (options.DYNAMIC.value) {
            //TODO: Check arity of DYNAMIC file
            List<String> dynFiles = options.DYNAMIC.value
            dynFiles.eachWithIndex { String dynFile, Integer index ->
                File f = new File(dynFile)
                FilenameUtils
                File dynImport = new File(outDir, "dynamic${index}.import")
                Helper.writeToFile dynImport, 
"""option,delimiter,"\t"
option,hasColumnNames,false

fromFile,"${f.getCanonicalPath()}",a,inv,b,type
toPredicate,Config:DynamicClass,type,inv"""

                lbScript.println("import -f $dynImport")
            }
        }

        if (!options.INCREMENTAL.value) {
            preprocessor.preprocess(this, analysisPath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            lbScript.println("addBlock -F ${name}-declarations.logic")

            if (options.SANITY.value) {
                lbScript.println('echo "-- Loading Sanity Rules --"')
                lbScript.println("addBlock -F ${Doop.doopLogic}/addons/sanity.logic")
            }
        }

        preprocessor.preprocess(this, analysisPath, "delta.logic", "${outDir}/${name}-delta.logic")
        lbScript.println("exec -F ${name}-delta.logic")

        if (!options.DISABLE_REFLECTION.value) {
            String reflectionPath = "${Doop.doopLogic}/core/reflection"

            preprocessor.preprocess(this, reflectionPath, "delta.logic", "${outDir}/reflection-delta.logic")
            lbScript.println("exec -F reflection-delta.logic")
            lbScript.println("exec -F ${reflectionPath}/allocations-delta.logic")
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
            lbScript.println("addBlock -F ${addonsPath}/dacapo/declarations.logic -B DacapoDecls")

            preprocessor.preprocess(this, addonsPath, "dacapo/delta.logic", "${outDir}/dacapo-delta.logic", macros)
            lbScript.println("exec -F dacapo-delta.logic")

            logger.info "Adding DaCapo rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "dacapo/rules.logic", "${outDir}/dacapo.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/dacapo.logic")
        }

        if (options.TAMIFLEX.value) {
            lbScript.println("addBlock -F ${addonsPath}/tamiflex/declarations.logic -B TamiflexDecls")
            lbScript.println("exec -F ${addonsPath}/tamiflex/delta.logic")

            logger.info "Adding tamiflex rules to addons logic"
            preprocessor.preprocess(this, addonsPath, "tamiflex/rules.logic", "${outDir}/tamiflex.logic", macros)
            Helper.appendAtFirst(this, "${outDir}/addons.logic", "${outDir}/tamiflex.logic")
        }

        if (options.CLIENT_EXCEPTION_FLOW.value) {
            preprocessor.preprocess(this, addonsPath, "exception-flow/declarations.logic",
                                    "${outDir}/exception-flow.logic",
                                    "exception-flow/rules.logic")
            lbScript.println("addBlock -F ${outdir}/exception-flow.logic")

            preprocessor.preprocess(this, addonsPath, "exception-flow/delta.logic",
                                    "${outDir}/exception-flow-delta.logic")
            lbScript.println("exec -F ${outDir}/exception-flow-delta.logic")
        }

        if (options.CLIENT_EXTENSIONS.value) {
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/declarations.logic",
                                    "${outDir}/client-extensions.logic")
            lbScript.println("addBlock -F ${outdir}/client-extensions.logic")

            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/delta.logic",
                                    "${outDir}/client-extensions-delta.logic")
            lbScript.println("exec -F ${outDir}/client-extensions-delta.logic")
        }

        //TODO: Log memory statistics

        if (options.REFINE.value) {
            refine()
        }

        if (!options.INCREMENTAL.value) {
            //TODO: Read the bloxopts
            if(isMustPointTo()) {
                if(options.MAY_PRE_ANALYSIS.value) {
                    String mayAnalysis = options.MAY_PRE_ANALYSIS.value;
                    
                    logger.info "Adding ${mayAnalysis} block"
                    lbScript.println("addBlock -F ${mayAnalysis}.logic")

                    logger.info "Adding may-related logic for ${name}"
                    lbScript.println("addBlock -F may-pre-analysis.logic")

                    // Default option for RootMethodForMustAnalysis.
                    // TODO: add command line option, so users can provide their own subset of root methods
                    logger.info "Adding block for RootMethodForMustAnalysis (default)"
                    lbScript.println("addBlock 'RootMethodForMustAnalysis(?meth) <- DeclaringClass:Method[?meth] = ?class, A    pplicationClass(?class), Reachable(?meth).'")
                }

                //TODO: Default Root Methods for 'simple' must-analyses.
                lbScript.println('echo "-- CFG Analysis Block --"')
                lbScript.println("addBlock -F ${Doop.doopLogic}/addons/cfg-analysis/declarations.logic")
                lbScript.println("addBlock -F ${Doop.doopLogic}/addons/cfg-analysis/rules.logic")
            }

            preprocessor.preprocess(this, analysisPath, "analysis.logic", "${outDir}/${name}.logic")
            Helper.appendAtFirst(this, "${outDir}/${name}.logic", "${outDir}/addons.logic")

            lbScript.println("commit")
            lbScript.println("elapsedTime")
            lbScript.println('echo "-- Main Analysis --"')
            lbScript.println("startTimer")
            lbScript.println("transaction")
            lbScript.println("addBlock -F ${name}.logic")
            lbScript.println("commit")
            lbScript.println("elapsedTime")
        }

        //TODO: Kill memory logger
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

        createDatabase()
        //TODO: We don't need to write-meta, do we?
        options.REFINE.value = true
        analyze()
    }

    protected void refine() {

        //The files and their contents
        Map<String, GString> files = [
                "refine-site": """option,delimiter,","
option,hasColumnNames,false
option,quotedValues,true
option,escapeQuotedValues,true

fromFile,"${outDir}/${name}-TempSiteToRefine.csv",CallGraphEdgeSource,CallGraphEdgeSource
toPredicate,SiteToRefine,CallGraphEdgeSource""",

                "negative-site": """option,delimiter,","
option,hasColumnNames,false

fromFile,"${outDir}/${name}-TempNegativeSiteFilter.csv",string,string
toPredicate,NegativeSiteFilter,string""",

                "refine-object": """option,delimiter,","
option,hasColumnNames,false
option,quotedValues,true
option,escapeQuotedValues,true

fromFile,"${outDir}/${name}-TempObjectToRefine.csv",HeapAllocation,HeapAllocation
toPredicate,ObjectToRefine,HeapAllocation""",

                "negative-object": """option,delimiter,","
option,hasColumnNames,false

fromFile,"${outDir}/${name}-TempNegativeObjectFilter.csv",string,string
toPredicate,NegativeObjectFilter,string"""
        ]

        logger.info "loading $name refinement facts "
        files.each { Map.Entry<String, GString> entry ->
            File f = new File(outDir, "${name}-${entry.key}.import")
            Helper.writeToFile f, entry.value
            Helper.checkFileOrThrowException(f, "Could not create import file: $f")
            lbScript.println("import -f $f")
        }
    }

    protected void runSetBased() {
        lbScript.println('echo "-- Transforming Input Facts --"')
        lbScript.println("addBlock -F ${Doop.doopLogic}/transform.logic")

        2.times { int i ->
            lbScript.println('echo "-- Transformation (step $i) --"')
            lbScript.println("exec -F ${Doop.doopLogic}/transform-delta.logic")
        }
    }

    protected void produceStats() {
        String statsPath = "${Doop.doopLogic}/addons/statistics"

        lbScript.println('echo "-- Producing Statistics --"')
        lbScript.println("startTimer")
        lbScript.println("transaction")

        preprocessor.preprocess(this, statsPath, "statistics-simple.logic", "${outDir}/statistics-simple.logic")
        lbScript.println("addBlock -F statistics-simple.logic")

        if (options.STATS.value) {
            preprocessor.preprocess(this, statsPath, "statistics.logic", "${outDir}/statistics.logic")
            lbScript.println("addBlock -F statistics.logic")
        }

        preprocessor.preprocess(this, statsPath, "delta.logic", "${outDir}/statistics-delta.logic")
        lbScript.println("exec -F statistics-delta.logic")

        lbScript.println("commit")
        lbScript.println("elapsedTime")
    }

    protected void runJPhantom(){
        logger.info "-- Running jphantom to generate complement jar --"

        String jar = jars[0].resolve().toString()
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
        jars[0] = new ResolvedDependency(f)
    }
    
    protected void runAverroes() {
        logger.info "-- Running averroes --"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)

        //We change linked arg and injar for soot in the runSoot method
    }

    protected void runSoot() {
        List<String> depArgs

        if (options.AVERROES.value) {
            //change linked arg and injar accordingly
            jars[0] = Helper.checkFileOrThrowException("$averroesDir/organizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar"]
        }
        else {
            List<String> deps = jars.drop(1).collect{ Dependency r -> ["-l", r.resolve()]}.flatten()
            List<String> links = jreLinkArgs()
            if (links.isEmpty()) {
                depArgs = ["-lsystem"] + deps 
            }
            else {
                depArgs = links.collect{ String arg -> ["-l", arg]}.flatten() + deps
            }

        }

        String[] params = ["-full", "-keep-line-number"] + depArgs + ["-application-regex", options.APP_REGEX.value]

        if (options.SSA.value) {
            params = params + ["-ssa"]
        }

        if (!options.RUN_JPHANTOM.value) {
            params = params + ["-allow-phantom"]
        }

        if (options.USE_ORIGINAL_NAMES.value) {
            params = params + ["-use-original-names"]
        }

        if (options.KEEP_LINE_NUMBER.value) {
            params = params + ["-keep-line-number"]
        }

        if (options.MAIN_CLASS.value) {
            params = params + ["-main", options.MAIN_CLASS.value]
        }

        params = params + ["-d", facts, jars[0].resolve()]

        logger.debug "Params of soot: ${params.join(' ')}"

        /*
        TODO: should we remove this?
        ClassLoader loader = sootClassLoader()
        sootTime = timing {
            //we invoke the main method reflectively to avoid adding soot as a compile-time dependency
            Helper.execJava(loader, "Main", params)
        }
        */
        sootTime = timing {
            doop.soot.Main.main(params)
        }
    }


    /**
     * Sets all exception options/flags to false. The exception options are determined by their flagType.
     */
    protected void disableAllExceptionOptions() {
        logger.debug "Disabling all exception preprocessor flags"
        options.values().each { AnalysisOption option ->
            if (option.forPreprocessor && option.flagType == PreprocessorFlag.EXCEPTION_FLAG) {
                option.value = false
            }
        }
    }
    
    /**
     * Sets all constant options/flags to false. The constant options are determined by their flagType.
     */
    protected void disableAllConstantOptions() {
        logger.debug "Disabling all constant preprocessor flags"
        options.values().each { AnalysisOption option ->
            if (option.forPreprocessor && option.flagType == PreprocessorFlag.CONSTANT_FLAG) {
                option.value = false
            }
        }
    }

    protected boolean isMustPointTo() {
        return name.equals("must-point-to")
    }

    /**
     * Creates a new class loader for running jphantom
     */
    private ClassLoader phantomClassLoader() {
        //TODO: for now, we hard-code the jphantom jar
        String jphantom = "${Doop.doopHome}/lib/jphantom-1.1-jar-with-dependencies.jar"
        File f = Helper.checkFileOrThrowException(jphantom, "jphantom jar missing or invalid: $jphantom")
        URL[] classpath = [f.toURI().toURL()]
        return new URLClassLoader(classpath)
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

        URL[] classpath = [f1.toURI().toURL(), f2.toURI().toURL()]
        return new URLClassLoader(classpath)
    }

    /**
     * Generates a list of the jre link arguments for soot
     */
    private List<String> jreLinkArgs() {

        String jre = options.JRE.value
        String path = "${Doop.doopHome}/externals/jre${jre}/lib"

        switch(jre) {
            case "1.3":
                return ["${path}/rt.jar"]
            case "1.4":
                return ["${path}/rt.jar", "${path}/jce.jar", "${path}/jsse.jar"]
            case "1.5":
                return ["${path}/rt.jar", "${path}/jce.jar", "${path}/jsse.jar"]
            case "1.6":
                return ["${path}/rt.jar", "${path}/jce.jar", "${path}/jsse.jar"]
            case "1.7":
                return ["${path}/rt.jar", "${path}/jce.jar", "${path}/jsse.jar", "${path}/rhino.jar"]
            case "system":
                /*
                String javaHome = System.getProperty("java.home")
                return ["$javaHome/lib/rt.jar", "$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
                */
                return []
            default:
                throw new RuntimeException("Invalid JRE version: $jre")
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
        List<String> libraryJars = jars.drop(1).collect { it.resolve().toString() } + jreAverroesLibraries()
        
        //Create the averroes properties
        Properties props = new Properties()
        props.setProperty("application_includes", options.APP_REGEX.value as String)
        props.setProperty("main_class", options.MAIN_CLASS as String)
        props.setProperty("input_jar_files", jars[0].resolve() as String)
        props.setProperty("library_jar_files", libraryJars.join(":"))

        //Concatenate the dynamic files
        if (options.DYNAMIC.value) {
            List<String> dynFiles = options.DYNAMIC.value
            File dynFileAll = new File(outDir, "all.dyn")
            dynFiles.each {String dynFile ->
                dynFileAll.append new File(dynFile).text
            }
            props.setProperty("dynamic_classes_file", dynFileAll as String)
        }

        props.setProperty("tamiflex_facts_file", options.TAMIFLEX.value as String)
        props.setProperty("output_dir", averroesDir as String)
        props.setProperty("jre", javaAverroesLibrary())
        
        new File(properties).newWriter().withWriter { Writer writer ->
            props.store(writer, null)
        }
        
        File f1 = Helper.checkFileOrThrowException(jar, "averroes jar missing or invalid: $jar")
        File f2 = Helper.checkFileOrThrowException(properties, "averroes properties missing or invalid: $properties")
        
        URL[] classpath = [f1.toURI().toURL(), f2.toURI().toURL()]
        return new URLClassLoader(classpath)
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
            default:
                throw new RuntimeException("Invalid JRE version: $jre")
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
        /**
         * Do only on the first invocation of bloxbatch; cache result
         */
        if (!bloxbatchVersion) {
            def interceptor = new SystemOutputInterceptor({ bloxbatchVersion = it; false})
            interceptor.start()
            Helper.execCommand("${options.BLOXBATCH.value} -version 2>&1 | awk 'BEGIN{flag=0} /BloxBatch/{flag=1} /Version/{if(flag){ printf \$2; flag=0 }}'", commandsEnvironment)
            interceptor.stop()

            def (major, minor) = bloxbatchVersion.tokenize(".")
            /**
             * We don't want the first 4 lines from stderr. The rest output of stderr might
             * have interesting messages though (i.e. errors). Use |& which pipes stderr
             * instead of stdout.
             * Another way to achieve that, in which we swap stdout and stderr using a third
             * file descriptor (3). In the end, 3>&- closes the extraneous file descriptor.
             * 3>&1 1>&2 2>&3 3>&- | tail -n +5
             * Because of piping, normally the exit status is that of tail. We want the exit
             * status of the first (0) command in the pipe sequence (bloxbatch)
             * ${PIPESTATUS[0]}
             */
            hasFilter = (major.toInteger() == 3 && minor.toInteger() >= 10)
        }

        // Always move to outDir first before executing command
        String command = "cd $outDir ; ${options.BLOXBATCH.value} -db $database $params"
        if (hasFilter)
            command += ' |& tail -n +5'
        if (pipeCommands)
            command += " | ${pipeCommands.join(" |")}"
        if (hasFilter)
            command += ' && [ ${PIPESTATUS[0]} = 0 ]'

        Helper.execCommand(command, commandsEnvironment)
    }

    /**
     * Helper method for making the code more readable
     */
    private long timing(Closure c) {
        return Helper.execWithTiming(logger, c)
    }
}
