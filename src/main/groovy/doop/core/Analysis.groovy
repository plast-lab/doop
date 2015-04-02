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
    
    File cacheFacts, cacheDatabase, database, exportDir, factsDir, averroesDir

    long sootTime, factsTime

    protected Analysis() {}

    String bloxbatchVersion, filter

    /**
     * Runs the analysis.
     */
    @Override
    void run() {
        preprocessor.init()

        //Dump the analysis in its "meta" file
        new File(outDir, "meta").withWriter { Writer w -> w.write(this.toString()) }
        
        initAnalysis()
        
        createDatabase()

        analyze()

        long dbSize = FileUtils.sizeOfDirectory(database) / 1024
        bloxbatch database, """-execute '+Stats:Runtime("100@ disk footprint (KB)", $dbSize).'"""

        File f = null
        try {
            f = Helper.checkFileOrThrowException("${Doop.doopLogic}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
        }
        catch(e) {
            logger.debug e.getMessage()
        }

        if(f) {
            logger.info "REANALYSE"
            reanalyze()                
        }

        produceStats()
    }

    /**
     * Prints the statistics. Mimics the behavior of the second part of the get-stats function of the original doop
     * script.
     */
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

    /**
     * Generates results. Mimics the behavior of the link-result function of the doop run script.
     */
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
               options.values().collect { AnalysisOption option -> option.toString() }.sort().join("\n")
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


    /**
     * Precprocess the logic files of the analysis. Mimics the behavior of the init-analysis function of the run script.
     */
    protected void initAnalysis() {

        logger.info "-- Pre-processing the logic files --"

        String basePath   = "${Doop.doopLogic}/analyses/${name}"
        String dacapoPath = "${Doop.doopLogic}/addons/dacapo"

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            preprocessor.preprocess(this, dacapoPath, "declarations.logic", "${outDir}/${name}-declarations.logic",
                                    "${basePath}/declarations.logic")
            preprocessor.preprocess(this, dacapoPath, "rules.logic",        "${outDir}/${name}.logic",
                                    "${basePath}/analysis.logic")
            preprocessor.preprocess(this, dacapoPath, "delta.logic",        "${outDir}/${name}-delta.logic",
                                    "${basePath}/delta.logic")
        }
        else {
            preprocessor.preprocess(this, basePath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            preprocessor.preprocess(this, basePath, "analysis.logic",     "${outDir}/${name}.logic")
            preprocessor.preprocess(this, basePath, "delta.logic",        "${outDir}/${name}-delta.logic")
        }

        //TODO: We don't need to calculate logic and input sums, do we?
        //TODO: We don't need to annotate db paths, do we?
        cacheFacts    = new File(outDir, "cacheFacts")
        cacheDatabase = new File(outDir, "cacheDatabase")
        database      = new File(outDir, "database")
        exportDir     = new File(outDir, "export")
        factsDir      = new File(outDir, "facts")
        averroesDir   = new File(outDir, "averroes")
    }

    /**
     * Creates the lb database. Mimics the behavior of the create-database function of the doop run script.
     */
    protected void createDatabase() {

        FileUtils.deleteQuietly(database)

        if (cacheDatabase.exists() && options.CACHE.value) {
            //Skip if cache database already exists
            logger.info "Using cached database $cacheDatabase"
        }
        else {
            //Generate facts
            if (cacheFacts.exists() && options.CACHE.value) {
                logger.info "Using cached facts $cacheFacts"
            }
            else if (options.CSV.value) {
                cacheFacts.mkdirs()
                Helper.moveDirectoryContents(exportDir, cacheFacts)
            }
            else {
                logger.info "Generating facts in $factsDir"

                FileUtils.deleteQuietly(factsDir)
                factsDir.mkdirs()

                if (!options.ALLOW_PHANTOM.value) {
                    runJPhantom()
                }

                if (options.AVERROES.value) {
                    runAverroes()
                }

                runSoot()

                FileUtils.deleteQuietly(cacheFacts)
                cacheFacts.mkdirs()
                Helper.copyDirectoryContents(factsDir, cacheFacts)
            }

            initDatabase()
        }

        database.mkdirs()
        FileUtils.copyDirectory(cacheDatabase, database)
    }

    protected void initDatabase() {

        logger.info "-- Database initialization --"

        if (options.INCREMENTAL.value) {
            File libDatabase = Helper.checkDirectoryOrThrowException("$outDir/libdb", "Preanalyzed library database is missing!")
            logger.info "Copying precomputed database of library from $libDatabase"
            timing {
                Helper.copyDirectoryContents(libDatabase, cacheDatabase)
            }
        }
        else {

            logger.info "Creating database in $cacheDatabase"
            timing {
                bloxbatch cacheDatabase, "-create -overwrite -blocks base"
            }

            logger.info "Loading fact declarations"
            timing {
                bloxbatch cacheDatabase, "-addBlock -file ${Doop.doopHome}/logic/facts/declarations.logic"
            }

            logger.info "Loading flow insensitivity declarations"
            timing {
                bloxbatch cacheDatabase, "-addBlock -file ${Doop.doopHome}/logic/facts/flow-insensitivity-declarations.logic"
            }

            logger.info "Loading facts"
            FileUtils.deleteQuietly(new File("facts"))
            Helper.execCommand("ln -s $cacheFacts facts", commandsEnvironment)

            FileUtils.touch(new File(cacheFacts, "ApplicationClass.facts"))
            FileUtils.touch(new File(cacheFacts, "Properties.facts"))

            factsTime = timing {
                bloxbatch cacheDatabase, "-execute -file ${Doop.doopHome}/logic/facts/entities-import.logic"
                bloxbatch cacheDatabase, "-execute -file ${Doop.doopHome}/logic/facts/import.logic"
            }
            bloxbatch cacheDatabase, """-execute '+Stats:Runtime("soot-fact-generation", $sootTime).'"""
            bloxbatch cacheDatabase, """-execute '+Stats:Runtime("loading facts time (sec)", $factsTime).'"""

            logger.info "Loading flow insensitivity delta"
            timing {
                bloxbatch cacheDatabase, "-execute -file ${Doop.doopHome}/logic/facts/flow-insensitivity-delta.logic"
            }

            if (options.MAIN_CLASS.value) {
                String mainClass = options.MAIN_CLASS.value
                logger.info "Setting main class to $mainClass"
                bloxbatch cacheDatabase, """-execute '+MainClass(x) <- ClassType(x), Type:fqn(x:"$mainClass").'"""
            }

            // Used to be FileUtils.deleteQuietly which erroneous deletes the
            // directory and not the symbolic link
            Helper.execCommand("unlink facts", commandsEnvironment)

            if (options.SET_BASED.value) {
                runSetBased()
            }
        }
    }

    /**
     * Performs the main part of the analysis. Mimics the behavior of the analyze function of the doop run script.
     */
    protected void analyze() {

        logger.info "-- Analysis Prologue --"

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

                bloxbatch database, "-import $dynImport"
            }

        }

        if (!options.INCREMENTAL.value) {

            logger.info "Loading ${name} declarations"
            timing {
                bloxbatch database, "-addBlock -file ${outDir}/${name}-declarations.logic"

                if (options.SANITY.value) {
                    logger.info "Loading sanity rules"
                    timing {
                        bloxbatch database, "-addBlock -file ${Doop.doopLogic}/addons/sanity.logic"
                    }
                }
            }
        }

        logger.info "Loading $name delta rules"
        long deltaTiming = timing {
            bloxbatch database, "-execute -file ${outDir}/${name}-delta.logic"
        }
        bloxbatch database, """-execute '+Stats:Runtime("$name delta rules time (sec)", $deltaTiming).'"""

        if (!options.DISABLE_REFLECTION.value) {
            String reflectionPath = "${Doop.doopLogic}/core/reflection"
            preprocessor.preprocess(this, reflectionPath, "delta.logic", "${outDir}/reflection-delta.logic")

            logger.info "Loading reflection delta rules"
            long time1 = timing {
                bloxbatch database, "-execute -file ${outDir}/reflection-delta.logic"
            }

            logger.info "Loading allocations delta rules"
            long time2 = timing {
                bloxbatch database, "-execute -file ${reflectionPath}/allocations-delta.logic"
            }

            long total = time1 + time2
            bloxbatch database, """-execute '+Stats:Runtime("reflection delta rules time (sec)", $total).'"""
        }

        String addonsPath = "${Doop.doopLogic}/addons"

        if (options.CLIENT_EXCEPTION_FLOW.value) {
            preprocessor.preprocess(this, addonsPath, "exception-flow/declarations.logic",
                                    "${outDir}/exception-flow.logic",
                                    "exception-flow/rules.logic")
            preprocessor.preprocess(this, addonsPath, "exception-flow/delta.logic",
                                    "${outDir}/exception-flow-delta.logic")
            
            logger.info "loading client exception-flow declarations & rules"
            timing {
                bloxbatch database, "-addblock -file ${outdir}/exception-flow.logic"
            }
            logger.info "Loading client exception-flow delta rules"
            timing {
                bloxbatch database, "-execute -file ${outDir}/exception-flow-delta.logic"
            }
        }

        if (options.CLIENT_EXTENSIONS.value) {
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/declarations.logic",
                                    "${outDir}/client-extensions.logic")
            preprocessor.preprocess(this, addonsPath, "auxiliary-heap-allocations/delta.logic",
                                    "${outDir}/client-extensions-delta.logic")

            logger.info "loading client extensions declarations & rules"
            timing {
                bloxbatch database, "-addblock -file ${outdir}/client-extensions.logic"
            }
            logger.info "Loading client extensions delta rules"
            timing {
                bloxbatch database, "-execute -file ${outDir}/client-extensions-delta.logic"
            }
        }

        //TODO: Log memory statistics

        if (options.REFINE.value) {
            refine()
        }

        logger.info "-- Analysis Main Phase --"

        if (!options.INCREMENTAL.value) {
            //TODO: Do we need the benchmark script?
            //TODO: Read the bloxopts
            if(isMustPointTo()) {
                if(options.MAY_PRE_ANALYSIS.value) {
                    String mayAnalysis = options.MAY_PRE_ANALYSIS.value;
                    
                    logger.info "Adding ${mayAnalysis} block"
                    timing {
                        bloxbatch database, "-addBlock -file ${outDir}/${mayAnalysis}.logic"
                    }
                    logger.info "Adding may-related logic for ${name}"
                    timing {
                        bloxbatch database, "-addBlock -file ${outDir}/may-pre-analysis.logic"
                    }

                    // Default option for RootMethodForMustAnalysis.
                    // TODO: add command line option, so users can provide their own subset of root methods
                    logger.info "Adding block for RootMethodForMustAnalysis (default)"
                    timing {
                        bloxbatch database, "-addBlock 'RootMethodForMustAnalysis(?meth) <- DeclaringClassMethod[?meth] = ?class, ApplicationClass(?class), Reachable(?meth).'"
                    }
                }

                //TODO: Default Root Methods for 'simple' must-analyses.
                logger.info "Adding cfg-analysis block"
                timing {
                    bloxbatch database, "-addBlock -file ${Doop.doopLogic}/addons/cfg-analysis/declarations.logic"
                    bloxbatch database, "-addBlock -file ${Doop.doopLogic}/addons/cfg-analysis/rules.logic"
                }
            }
            logger.info "Main analysis"
            long time = timing {
                bloxbatch database, "-addBlock -file ${outDir}/${name}.logic"
            }
            bloxbatch database, """-execute '+Stats:Runtime("benchmark time(sec)", $time).'"""
        }

        //TODO: Run client extensions

        //TODO: Kill memory logger
    }

    /**
     * Reanalyze. Mimics the behavior of the reanalyze function of the doop run script.
     */
    protected void reanalyze() {
        logger.info "Loading ${name} refinement-delta rules"

        preprocessor.preprocess(this, "${Doop.doopLogic}/${name}", "refinement-delta.logic", "${outDir}/${name}-refinement-delta.logic")

        timing {
            bloxbatch database, "-execute -file ${outDir}/${name}-refinement-delta.logic"
        }

        timing {
            bloxbatch database, "-exportCsv TempSiteToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatch database, "-exportCsv TempNegativeSiteFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatch database, "-exportCsv TempObjectToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        timing {
            bloxbatch database, "-exportCsv TempNegativeObjectFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        createDatabase()
        //TODO: We don't need to write-meta, do we?
        options.REFINE.value = true
        analyze()
    }


    /**
     * Activates refinement logic.
     * Mimics the behavior of the bin/refine script.
     */
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
            bloxbatch database, "-import $f"
        }
    }

    /**
     * Activates set-based logic that removes redundant input facts.
     * Mimics the behavior of the bin/set-based script.
     */
    protected void runSetBased() {

        logger.info "Preprocessing/transforming input facts: analysis"
        timing {
            bloxbatch cacheDatabase, "-addBlock -file ${Doop.doopLogic}/transform.logic"
        }

        2.times { int i ->
            logger.info "Preprocessing/transforming input facts: transformation (step $i)"
            timing {
                bloxbatch cacheDatabase, "-execute -file ${Doop.doopLogic}/transform-delta.logic"
            }
        }
    }

    /**
     * Produces the statistics. Mimics the behavior of the first part of the get-stats function of the original doop
     * scrip.
     */
    protected void produceStats() {
        String statsPath = "${Doop.doopLogic}/addons/statistics"

        preprocessor.preprocess(this, statsPath, "statistics-simple.logic", "${outDir}/statistics-simple.logic")
        preprocessor.preprocess(this, statsPath, "delta.logic", "${outDir}/statistics-delta.logic")

        logger.info "Loading simple statistics declarations"
        long time1 = timing {
            bloxbatch database, "-addBlock -file ${outDir}/statistics-simple.logic"
        }
        long time2 = 0

        if (options.STATS.value) {
            preprocessor.preprocess(this, statsPath, "statistics.logic", "${outDir}/statistics.logic")
            time2 = timing {
                bloxbatch database, "-addBlock -file ${outDir}/statistics.logic"
            }
        }

        logger.info "Loading statistics delta rules"
        long time3 = timing {
            bloxbatch database, "-execute -file ${outDir}/statistics-delta.logic"
        }

        long total = time1 + time2 + time3
        bloxbatch database, """-execute '+Stats:Runtime("statistics time (sec)", $total).'"""
    }


    /**
     * Runs jphantom if phantom refs are not allowed
     */
    protected void runJPhantom(){
        logger.info "Running jphantom to generate complement jar"

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
    
    /**
     * Runs averroes, if specified
     */
    protected void runAverroes() {
        logger.info "Running averroes"

        ClassLoader loader = averroesClassLoader()
        Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)

        //We change linked arg and injar for soot in the runSoot method
    }

    /**
     * soot fact generation
     */
    protected void runSoot() {

        logger.info "-- Running soot to generate facts --"

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

        if (options.ALLOW_PHANTOM.value) {
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

        params = params + ["-d", factsDir, jars[0].resolve()]

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
     * Invokes bloxbatch on the given database with the given params. Helper method for making the code more readable.
     */
    private void bloxbatch(File database, String params) {
        bloxbatchPipe(database, params)
    }

    /**
     * Invokes bloxbatch on the given database with the given params, piping it up with supplied pipeCommands.
     * Helper method for making the code more readable.
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
             */
            filter = (major.toInteger() == 3 && minor.toInteger() >= 10) ? "|& tail -n +5 " : ""
        }

        if (pipeCommands) {
            Helper.execCommand("${options.BLOXBATCH.value} -db $database $params $filter | ${pipeCommands.join(" |")}", commandsEnvironment)
        }
        else {
            Helper.execCommand("${options.BLOXBATCH.value} -db $database $params $filter", commandsEnvironment)
        }
    }

    /**
     * Helper method for making the code more readable
     * @param c
     */
    private long timing(Closure c) {
        return Helper.execWithTiming(logger, c)
    }

}
