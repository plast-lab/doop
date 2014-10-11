package doop

import doop.preprocess.Preprocessor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 9/7/2014
 *
 * A DOOP analysis that holds all the relevant options (vars, paths, etc) and implements all the relevant steps.
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
     * The output dir of the analysis (results and intermediate files)
     */
    String outDir

    /**
     * The options of the analysis
     */
    Map<String, AnalysisOption> options

    /**
     * The jar files of the analysis
     */
    List<File> jars

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
	
	File cacheFacts, cacheDatabase, database, humanDatabase, exportDir, factsDir, averroesDir

    long sootTime, factsTime

    protected Analysis() {}

    /**
     * Runs the analysis.
     */
    @Override
    void run() {
        Helper.execWithTiming (logger) {
			
			initAnalysis()
			
            createDatabase()

            //TODO: We don't need the write-meta staff, do we?
			
			analyze()

            long dbSize = FileUtils.sizeOfDirectory(database) * 1024
            bloxbatch database, "-execute '+Stats:Runtime(\"100@ disk footprint (KB)\", $size).'"

            //TODO: We don't need to link-result, do we?

            getStats()

            File f = null
            try {
                f = Helper.checkFileOrThrowException("${Doop.doopLogic}/${name}/refinement-delta.logic", "No refinement-delta.logic for ${name}")
            }
            catch(e) {
                logger.debug e.getMessage()
            }

            if(f) {
                reanalyze()
                getStats()
            }
        }
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
     * Precprocess the logic files of the analysis. Mimics the behavior of the init-analysis function of the run script.
     */
    protected void initAnalysis() {

        logger.info "Pre-processing the logic files"

        String basePath = "${Doop.doopLogic}/${name}"
        String baseLibPath = "${Doop.doopLogic}/library"
        String baseClientPath = "${Doop.doopLogic}/client"
        String dacapoPath = "${Doop.doopLogic}/dacapo"

        if (options.DACAPO.value || options.DACAPO_BACH.value) {
            preprocessor.preprocess(this, dacapoPath, "declarations.logic",
                                    "${outDir}/${name}-declarations.logic",
                                    "${basePath}/declarations.logic")
            preprocessor.preprocess(this, dacapoPath, "delta.logic",
                                    "${outDir}/${name}-delta.logic",
                                    "${basePath}/delta.logic")
            preprocessor.preprocess(this, dacapoPath, "rules.logic",
                                    "${outDir}/${name}.logic",
                                    "${basePath}/analysis.logic")
        }
        else {
            preprocessor.preprocess(this, basePath, "declarations.logic", "${outDir}/${name}-declarations.logic")
            preprocessor.preprocess(this, basePath, "delta.logic",        "${outDir}/${name}-delta.logic")
            preprocessor.preprocess(this, basePath, "analysis.logic",     "${outDir}/${name}.logic")
        }

        preprocessor.preprocess(this, baseLibPath,    "reflection-delta.logic",     "${outDir}/reflection-delta.logic")
        preprocessor.preprocess(this, baseClientPath, "exception-flow-delta.logic", "${outDir}/exception-flow-delta.logic")
        preprocessor.preprocess(this, baseClientPath, "auxiliary-heap-allocations-delta.logic",
                                "${outDir}/auxiliary-heap-allocations-delta.logic"
        )

        //TODO: We don't need to calculate logic and input sums, do we?

        //TODO: We don't need to annotate db paths, do we?

		cacheFacts    = new File(outDir, "cacheFacts")
		cacheDatabase = new File(outDir, "cacheDatabase")
		database      = new File(outDir, "database")
		humanDatabase = new File(outDir, "humanDatabase")
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
                //mv exportDir/* to cacheFacts
                Helper.moveDirectoryContents(exportDir, cacheFacts)
			}
			else {
                logger.info "Generating facts in $cacheFacts"

                FileUtils.deleteQuietly(factsDir)
                factsDir.mkdirs()

                runJPhantom()

                runAverroes()

                runSoot()

                cacheFacts.mkdirs()
                Helper.moveDirectoryContents(factsDir, cacheFacts)
            }

            logger.info "Database initialization"

            initDatabase()
		}

        database.mkdirs()
        FileUtils.copyDirectory(cacheDatabase, database)
    }

    /**
     * Performs the main part of the analysis. Mimics the behavior of the analyze function of the doop run script.
     */
    protected void analyze() {

        logger.info "Analysis Prologue"

        //TODO: Support multiple values for DYNAMIC
        if (options.DYNAMIC.value) {

            //TODO: Check arity of DYNAMIC file

            File dynImport = new File(outDir, "dynamic.import")
            dynImport.newWriter().withWriter { Writer writer ->
                writer.write """
option,delimeter,"\t"
option,hasColumnNames,false

fromFile,"${options.DYNAMIC.value}",a,inv,b,type
toPredicate,Config:DynamicClass,type,inv
"""
            }

            bloxbatch database, "-import $dynImport"
        }

        if (!options.INCREMENTAL.value) {
            logger.info "Loading $name declarations"
            Helper.execWithTiming(logger) {
                bloxbatch database, "-addBlock -file ${outDir}/${name}-declations.logic"

                if (options.SANITY.value) {
                    logger.info "Loading sanity rules"
                    Helper.execWithTiming(logger) {
                        bloxbatch database, "-addBlock -file ${Doop.doopLogic}/library/sanity.logic"
                    }
                }
            }
        }

        logger.info "Loading $name delta rules"
        long deltaTiming = Helper.execWithTiming(logger) {
            bloxbatch database, "-execute -file ${outDir}/${name}-delta.logic"
        }
        bloxbatch database, "-execute '+Stats:Runtime(\"$name delta rules time (sec)\", $deltaTiming).'"

        if (!options.DISABLE_REFLECTION.value) {
            logger.info "Loading reflection delta rules"
            long time1 = Helper.execWithTiming(logger) {
                bloxbatch database, "-execute -file ${outDir}/reflection-delta.logic"
            }

            logger.info "Loading allocations delta rules"
            long time2 = Helper.execWithTiming(logger) {
                bloxbatch database, "-execute -file ${Doop.doopLogic}/library/reflection/allocations-delta.logic"
            }

            long total = time1 + time2
            bloxbatch database, "-execute '+Stats:Runtime(\"reflection delta rules time (sec)\", $total).'"
        }

        logger.info "Loading client delta rules"
        Helper.execWithTiming(logger) {
            bloxbatch database, "-execute -file ${outDir}/exception-flow-delta.logic"
        }

        logger.info "Loading auxiliary delta rules"
        Helper.execWithTiming(logger) {
            bloxbatch database, "-execute -file ${outDir}/auxiliary-heap-allocations-delta.logic"
        }

        //TODO: Log memory statistics

        //TODO: Run refinement logic

        logger.info "Analysis Main Phase"

        if (!options.INCREMENTAL.value) {
            //TODO: Do we need the benchmark script?
            //TODO: Read the bloxopts
            long time = Helper.execWithTiming(logger) {
                bloxbatch database, "-addBlock -file ${outDir}/${name}.logic"
            }
            bloxbatch database, "-execute '+Stats:Runtime(\"benchmark time(sec)\", $time).'"
        }

        //TODO: Run client extensions

        //TODO: Kill memory logger
    }

    /**
     * Gets the statistics. Mimics the behavior of the get-stats function of the doop run script.
     */
    protected void getStats() {

        String baseLibPath = "${Doop.doopLogic}/library"

        preprocessor.preprocess(this, baseLibPath, "statistics-simple.logic", "${outDir}/statistics-simple.logic")
        preprocessor.preprocess(this, baseLibPath, "statistics-delta.logic", "${outDir}/statistics-delta.logic")

        long time1 = Helper.execWithTiming(logger) {
            bloxbatch database, "-addBlock -file ${outDir}/statistics-simple.logic >/dev/null"
        }
        long time2 = 0

        if (options.STATS.value) {
            preprocessor.preprocess(this, baseLibPath, "statistics.logic", "${outDir}/statistics.logic")
            time2 = Helper.execWithTiming(logger) {
                bloxbatch database, "-addBlock -file ${outDir}/statistics.logic >/dev/null"
            }
        }

        long time3 = Helper.execWithTiming(logger) {
            bloxbatch database, "execute -file ${outDir}/statistics-delta.logic >/dev/null"
        }

        long total = time1 + time2 + time3
        bloxbatch database, "-execute '+Stats:Runtime(\"statistics time (sec)\", $total).'"

        logger.info "Runtime metrics"
        bloxbatch database, """-query Stats:Runtime | sort -n | sed -r 's/^ +([0-9]+[ab]?@ )?//' | awk -F ', ' '{ printf("%-80s %'"'"'.2f\\n", \$1, \$2) }'"""

        logger.info "Statistics"
        bloxbatch database, """-query Stats:Metrics | sort -n | sed -r 's/^ +[0-9]+[ab]?@ //' | awk -F ', ' '{ printf("%-80s %'"'"'d\\n", \$1, \$2) }'"""
    }

    /**
     * Reanalyze. Mimics the behavior of the reanalyze function of the doop run script.
     */
    protected void reanalyze() {
        logger.info "Loading ${name} refinement-delta rules"

        preprocessor.preprocess(this, "${Doop.doopLogic}/${name}", "refinement-delta.logic", "${outDir}/${name}-refinement-delta.logic")

        Helper.execWithTiming(logger) {
            bloxbatch database, "-execute -file ${outDir}/${name}-refinement-delta.logic"
        }

        Helper.execWithTiming(logger) {
            bloxbatch database, "-exportCsv TempSiteToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        Helper.execWithTiming(logger) {
            bloxbatch database, "-exportCsv TempNegativeSiteFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        Helper.execWithTiming(logger) {
            bloxbatch database, "-exportCsv TempObjectToRefine -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        Helper.execWithTiming(logger) {
            bloxbatch database, "-exportCsv TempNegativeObjectFilter -overwrite -exportDataDir $outDir -exportFilePrefix ${name}-"
        }

        createDatabase()
        //TODO: We don't need to write-meta, do we?
        options.REFINE.value = true
        analyze()
    }


    /**
     * Runs jphantom if phantom refs are not allowed
     */
    protected void runJPhantom(){
        if (!options.ALLOW_PHANTOM.value) {

            logger.info "Running jphantom to generate complement jar"

            String jar = jars[0]
            String jarName = FilenameUtils.getBaseName(jar)
            String jarExt = FilenameUtils.getExtension(jar)
            String newJar = "${jarName}-complemented.${jarExt}"
            String[] params = [jar, "-o", "${outDir}/$newJar", "-d", "${outDir}/phantoms", "-v", "0"]
            logger.debug "Params of jphantom: ${params.join(' ')}"

            //we invoke the main method reflectively to avoid adding jphantom as a compile-time dependency
            ClassLoader loader = phantomClassLoader()
            Helper.execJava(loader, "jphantom.Driver", params)

            //set the jar of the analysis to the complemented one
            jars[0] = Helper.checkFileOrThrowException("$outDir/$newJar", "jphantom invocation failed")
        }
    }
	
	/**
	 * Runs averroes, if specified
	 */
	protected void runAverroes() {
		if (options.AVERROES.value) {
			logger.info "Running averroes"
			
			ClassLoader loader = averroesClassLoader()
			Helper.execJava(loader, "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader", null)
			
			//We change linked arg and injar for soot in the runSoot method
		}
	}

    /**
     * soot fact generation
     */
    protected void runSoot() {

        logger.info "Running soot to generate facts"

        List<String> depArgs

        if (options.AVERROES.value) {
            //change linked arg and injar accordingly
            jars[0] = Helper.checkFileOrThrowException("$averroesDir/oranizedApplication.jar", "Averroes invocation failed")
            depArgs = ["-l", "$averroesDir/placeholderLibrary.jar"]
        }
        else {
            List<File> deps = jars.drop(1)
            List<String> links = jreLinkArgs()
            depArgs = deps.collect{ File f -> ["-l", f]}.flatten() + links.collect{ String arg -> ["-l", arg]}.flatten()
        }

        String[] params = ["-full"] + depArgs + ["-application-regex", options.APP_REGEX.value]

        if (options.SSA.value) {
            params = params + ["-ssa"]
        }

        if (options.ALLOW_PHANTOM.value) {
            params = params + ["-allow-phantom"]
        }

        if (options.MAIN_CLASS.value) {
            params = params + ["-main", options.MAIN_CLASS.value]
        }

        params = params + ["-d", factsDir, jars[0]]

        logger.debug "Params of soot: ${params.join(' ')}"

        ClassLoader loader = sootClassLoader()
        sootTime = Helper.execWithTiming(logger) {
            //we invoke the main method reflectively to avoid adding soot as a compile-time dependency
            Helper.execJava(loader, "Main", params)
        }
    }


    protected void initDatabase() {

        if (options.INCREMENTAL.value) {
            File libDatabase = Helper.checkDirectoryOrThrowException("$outDir/libdb", "Preanalyzed library database is missing!")
            logger.info "Copying precomputed database of library from $libDatabase"
            Helper.execWithTiming(logger) {
                Helper.copyDirectoryContents(libDatabase, cacheDatabase)
            }
        }
        else {

            logger.info "Creating database in $cacheDatabase"
            Helper.execWithTiming(logger) {
                bloxbatch cacheDatabase, "-create -overwrite -blocks base"
            }

            logger.info "Loading fact declarations"
            Helper.execWithTiming(logger) {
                bloxbatch cacheDatabase, "-addBlock -file ${Doop.doopHome}/logic/library/fact-declarations.logic"
            }

            logger.info "Loading facts"
            FileUtils.deleteDirectory(factsDir)
            Helper.execCommand("ln -s $cacheFacts $factsDir", commandsEnvironment)

            FileUtils.touch(new File(factsDir, "ApplicationClass.facts"))
            FileUtils.touch(new File(factsDir, "Properties.facts"))


            File importFile = new File("$outDir/fact-declarations.import")
            importFile.withWriter { Writer writer ->
                ImportGenerator.Type type = options.CSV.value ? ImportGenerator.Type.CSV : ImportGenerator.Type.TEXT
                new ImportGenerator(type, writer).generate()
            }
            Helper.checkFileOrThrowException(importFile, "Could not generate import file: $importFile")

            factsTime = Helper.execWithTiming(logger) {
                bloxbatch cacheDatabase, "-import $importFile"
            }

            bloxbatch cacheDatabase, "-execute '+Stats:Runtime(\"soot-fact-generation time (sec)\", $sootTime).'"
            bloxbatch cacheDatabase, "-execute '+Stats:Runtime(\"loading facts time (sec)\", $factsTime).'"

            FileUtils.deleteQuietly(factsDir)

            if (options.MAIN_CLASS.value) {
                String mainClass = options.MAIN_CLASS.value
                logger.info "Setting main class to $mainClass"
                bloxbatch cacheDatabase, "-execute '+MainClass(x) <- ClassType(x), Type:Value(x:\"$mainClass\").'"
            }

            //TODO: run set-based logic
        }
    }

	/*
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
	
	/*
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

	/**
     * Creates a new class loader for running jphantom
     */
    private ClassLoader phantomClassLoader() {
        //TODO: for now, we hard-code the jphantom jar
        String jphantom = "${Doop.doopHome}/lib/jphantom-1.0-jar-with-dependencies.jar"
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

        //TODO: deal with JRE versions other than system

        if (options.JRE.value == "system") {
            String javaHome = System.getProperty("java.home")
            return ["$javaHome/lib/rt.jar", "$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
        }
        else {
            throw new RuntimeException("Only system JRE is currently supported")
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
		List<String> libraryJars = jars.drop(1).collect { it.toString() } + jreAverroesLibraries()
		
		//Create the averroes properties
		Properties props = new Properties()
		props.setProperty("application_includes", options.APP_REGEX.value as String)
		props.setProperty("main_class", options.MAIN_CLASS as String)
		props.setProperty("input_jar_files", jars[0] as String)
		props.setProperty("library_jar_files", libraryJars.join(":"))
		props.setProperty("dynamic_classes_file", options.DYNAMIC.value as String)
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
		//TODO: deal with JRE versions other than system

        if (options.JRE.value == "system") {
            String javaHome = System.getProperty("java.home")
            return ["$javaHome/lib/jce.jar", "$javaHome/lib/jsse.jar"]
        }
        else {
            throw new RuntimeException("Only system JRE is currently supported")
        }
	}	
	
	/**
	 * Generates the full path to the rt.jar required by averroes
	 */
	private String javaAverroesLibrary() {
		//TODO: deal with JRE versions other than system

        if (options.JRE.value == "system") {
            String javaHome = System.getProperty("java.home")
            return "$javaHome/lib/rt.jar"
        }
        else {
            throw new RuntimeException("Only system JRE is currently supported")
        }
	}

    /**
     * Invokes bloxbatch on the given database with the given params. Helper method for making the code more readable.
     */
    private void bloxbatch(File database, String params) {
        Helper.execCommand("${options.BLOXBATCH.value} -db $database $params", commandsEnvironment)
    }
}
