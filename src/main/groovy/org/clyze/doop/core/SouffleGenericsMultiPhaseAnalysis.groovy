package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.transform.TypeChecked
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.utils.CPreprocessor
import org.clyze.doop.utils.DDlog
import org.clyze.doop.utils.SouffleOptions
import org.clyze.utils.Executor

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory
import static org.apache.commons.io.FilenameUtils.getBaseName

@CompileStatic
@InheritConstructors
@Log4j
@TypeChecked
class SouffleGenericsMultiPhaseAnalysis extends SouffleAnalysis {

	@Override
	void run() {
		File preAnalysis = new File(outDir, "context-insensitive.dl")
		//File preAnalysis = new File(outDir, "2-object-sensitive+heap.dl")
		deleteQuietly(preAnalysis)
		preAnalysis.createNewFile()
		File analysis = new File(outDir, "${name}.dl")
		deleteQuietly(analysis)
		analysis.createNewFile()

		options.CONFIGURATION.value = "ContextInsensitiveConfiguration"
		//options.CONFIGURATION.value = "TwoObjectSensitivePlusHeapConfiguration"
		def commandsEnv = initExternalCommandsEnvironment(options)

		executor = new Executor(outDir, commandsEnv)
		cpp = new CPreprocessor(this, executor)

		initDatabase(preAnalysis)
		log.debug("analysis: ${getBaseName(preAnalysis.name)}")
		runAnalysisAndProduceStats(preAnalysis)

		def script = newScriptForAnalysis(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		SouffleOptions souffleOpts = new SouffleOptions(options)
		if (!options.FACTS_ONLY.value) {
			if (options.VIA_DDLOG.value) {
				// Copy the DDlog converter, needed both for logic
				// compilation and fact post-processing.
				DDlog.copyDDlogConverter(log, outDir)
			}
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile0 = script.compile(preAnalysis, outDir, souffleOpts)
					log.info "[Task COMPILE Done]"
					return generatedFile0
				}
			})
		}

		File runtimeMetricsFile = new File(database, "Stats_Runtime.csv")

		def generatedFile0
		if (options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			generatedFile0 = compilationFuture.get()
			System.gc()
		}

		try {
			log.info "[Task FACTS...]"
			generateFacts()
			script.postprocessFacts(outDir, souffleOpts.profile)
			log.info "[Task FACTS Done]"

			if (options.FACTS_ONLY.value) return

			if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			    generatedFile0 = compilationFuture.get()
            }
			script.run(generatedFile0, factsDir, outDir,
					(options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure, souffleOpts)

			int dbSize = (sizeOfDirectory(database) / 1024).intValue()
			runtimeMetricsFile.createNewFile()
			runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
			runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
			runtimeMetricsFile.append("fact generation time (sec)\t$factGenTime\n")
		} finally {
			executorService.shutdownNow()
		}
		printStats()

		options.INPUT_ID.value = factsDir
		options.CONFIGURATION.value = "AdaptiveTwoObjectSensitivePlusHeapConfiguration"
		options.GENERICS_PRE_ANALYSIS.value = null
		options.PRECISE_GENERICS.value = true
		options.CFG_ANALYSIS.value = false
		executor = new Executor(outDir, commandsEnv)
		cpp = new CPreprocessor(this, executor)
		//ScalerPostAnalysis scalerPostAnalysis = new ScalerPostAnalysis(database)
		//scalerPostAnalysis.run(factsDir)
//		def insensitiveMethodsFile = new File(database.getAbsolutePath() + File.separator + "InsensitiveMethod.csv")
//		def stickyContextMethodsFile = new File(database.getAbsolutePath() + File.separator + "StickyContextMethod.csv")
//		def entryContextMethodsFile = new File(database.getAbsolutePath() + File.separator + "EntryContextMethod.csv")
//
//		def insensitiveMethodsFactFile = new File(factsDir.getAbsolutePath() + File.separator + "InsensitiveMethod.facts")
//		def stickyContextMethodsFactFile = new File(factsDir.getAbsolutePath() + File.separator + "StickyContextMethod.facts")
//		def entryContextMethodsFactFile = new File(factsDir.getAbsolutePath() + File.separator + "EntryContextMethod.facts")
//
//		Files.copy(insensitiveMethodsFile.toPath(), insensitiveMethodsFactFile.toPath())
//		Files.copy(stickyContextMethodsFile.toPath(), stickyContextMethodsFactFile.toPath())
//		Files.copy(entryContextMethodsFile.toPath(), entryContextMethodsFactFile.toPath())

		def mapAcceptsValueType = new File(database.getAbsolutePath() + File.separator + "MapAcceptsValueType.csv")
		def mapAcceptsKeyType = new File(database.getAbsolutePath() + File.separator + "MapAcceptsKeyType.csv")
		def collectionAcceptsValueType = new File(database.getAbsolutePath() + File.separator + "CollectionAcceptsValueType.csv")

		def mapAcceptsValueTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "MapAcceptsValueType.facts")
		def mapAcceptsKeyTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "MapAcceptsKeyType.facts")
		def collectionAcceptsValueTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "CollectionAcceptsValueType.facts")

		Files.copy(mapAcceptsValueType.toPath(), mapAcceptsValueTypeFacts.toPath())
		Files.copy(mapAcceptsKeyType.toPath(), mapAcceptsKeyTypeFacts.toPath())
		Files.copy(collectionAcceptsValueType.toPath(), collectionAcceptsValueTypeFacts.toPath())

		def mapAcceptsValueFallbackType = new File(database.getAbsolutePath() + File.separator + "MapAcceptsValueFallbackType.csv")
		def mapAcceptsKeyFallbackType = new File(database.getAbsolutePath() + File.separator + "MapAcceptsKeyFallbackType.csv")
		def collectionAcceptsValueFallbackType = new File(database.getAbsolutePath() + File.separator + "CollectionAcceptsValueFallbackType.csv")

		def mapAcceptsValueFallbackTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "MapAcceptsValueFallbackType.facts")
		def mapAcceptsKeyFallbackTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "MapAcceptsKeyFallbackType.facts")
		def collectionAcceptsValueFallbackTypeFacts = new File(factsDir.getAbsolutePath() + File.separator + "CollectionAcceptsValueFallbackType.facts")

		Files.copy(mapAcceptsValueFallbackType.toPath(), mapAcceptsValueFallbackTypeFacts.toPath())
		Files.copy(mapAcceptsKeyFallbackType.toPath(), mapAcceptsKeyFallbackTypeFacts.toPath())
		Files.copy(collectionAcceptsValueFallbackType.toPath(), collectionAcceptsValueFallbackTypeFacts.toPath())

		initDatabase(analysis)
		log.debug("analysis: ${getBaseName(analysis.name)}")
		runAnalysisAndProduceStats(analysis)

		compilationFuture = null
		executorService = Executors.newSingleThreadExecutor()
		if (!options.FACTS_ONLY.value) {
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysis, outDir, souffleOpts)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		runtimeMetricsFile = new File(database, "Stats_Runtime.csv")

		def generatedFile
		if (options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			generatedFile = compilationFuture.get()
			System.gc()
		}
		try {
			if (options.FACTS_ONLY.value) return

			if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
				generatedFile = compilationFuture.get()
			}
			script.run(generatedFile, factsDir, outDir,
					   (options.X_MONITORING_INTERVAL.value as long) * 1000, monitorClosure, souffleOpts)

			int dbSize = (sizeOfDirectory(database) / 1024).intValue()
			runtimeMetricsFile.createNewFile()
			runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
			runtimeMetricsFile.append("disk footprint (KB)\t$dbSize\n")
			runtimeMetricsFile.append("fact generation time (sec)\t$factGenTime\n")
		} finally {
			executorService.shutdownNow()
		}
	}

	/**
	 * Initializes the external commands environment of the given analysis, by:
	 * <ul>
	 *     <li>adding the LD_LIBRARY_PATH option to the current environment
	 *     <li>modifying PATH to also include the LD_LIBRARY_PATH option
	 *     <li>adding the LOGICBLOX_HOME option to the current environment
	 *     <li>adding the DOOP_HOME to the current environment
	 *     <li>adding the LB_PAGER_FORCE_START and the LB_MEM_NOWARN to the current environment
	 *     <li>adding the variables/paths/tweaks to meet the lb-env-bin.sh requirements of the pa-datalog distro
	 * </ul>
	 */
	protected static Map<String, String> initExternalCommandsEnvironment(Map<String, AnalysisOption<?>> options) {
		log.debug "Initializing the environment of the external commands"

		Map<String, String> env = [:]
		env.putAll(System.getenv())

		env.LC_ALL = "en_US.UTF-8"

		if (options.X_LB3.value) {
			def lbHome = options.LOGICBLOX_HOME.value
			env.LOGICBLOX_HOME = lbHome
			//We add these LB specific env vars here to make the server deployment more flexible (and the cli user's life easier)
			env.LB_PAGER_FORCE_START = "true"
			env.LB_MEM_NOWARN = "true"
			env.DOOP_HOME = Doop.doopHome

			//We add the following for pa-datalog to function properly (copied from the lib-env-bin.sh script)
			def path = env.PATH
			env.PATH = "${lbHome}/bin:${path ?: ""}" as String

			def ldLibraryPath = options.LD_LIBRARY_PATH.value
			env.LD_LIBRARY_PATH = "${lbHome}/lib/cpp:${ldLibraryPath ?: ""}" as String
		}

		return env
	}

	void printStats() {
		List<String> lines = []

		File file = new File("${this.outDir}/database/Stats_Runtime.csv")
		file.eachLine { String line -> lines << line.replace("\t", ", ") }

		log.info "-- Runtime metrics --"
		lines.sort()*.split(", ").each {
			printf("%-80s %,d\n", it[0], it[1] as long)
		}

			lines = []

			file = new File("${this.outDir}/database/Stats_Metrics.csv")
			file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }

			log.info "-- Statistics --"
			lines.sort()*.split(", ").each {
				printf("%-80s %,d\n", it[1], it[2] as long)
			}
	}
}
