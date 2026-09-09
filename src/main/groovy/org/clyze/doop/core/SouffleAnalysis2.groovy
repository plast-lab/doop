package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.jimple.JimpleProcessor
import org.clyze.doop.soot.DoopConventions
import org.clyze.doop.utils.ConfigurationGenerator
import org.clyze.doop.utils.DDlog
import org.clyze.doop.utils.SouffleOptions
import org.clyze.doop.utils.SouffleScript
import org.clyze.doop.utils.TACGenerator
import org.clyze.doop.utils.XTractor
import org.clyze.utils.Executor
import org.clyze.utils.JHelper

import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.apache.commons.io.FileUtils.sizeOfDirectory
import static org.apache.commons.io.FilenameUtils.getBaseName

@CompileStatic
@InheritConstructors
@Log4j
class SouffleAnalysis2 extends SouffleCompatibleAnalysis {

	@Override
	protected void doRun(File analysisFile, File runtimeMetricsFile) {

		SouffleScript script = newScriptForAnalysis(executor)

		Future<File> compilationFuture = null
		def executorService = Executors.newSingleThreadExecutor()
		String analysisBinaryPath = options.USE_ANALYSIS_BINARY.value as String
		boolean runInterpreted = options.SOUFFLE_MODE.value == DoopAnalysisFamily.SOUFFLE_INTERPRETED
		long monitorInterval = (options.X_MONITORING_INTERVAL.value as long) * 1000
		SouffleOptions souffleOpts = new SouffleOptions(options)

		if (!options.FACTS_ONLY.value && !analysisBinaryPath && !runInterpreted) {
			if (options.VIA_DDLOG.value) {
				// Copy the DDlog converter, needed both for logic
				// compilation and fact post-processing.
				DDlog.copyDDlogConverter(log, outDir)
			}
			compilationFuture = executorService.submit(new Callable<File>() {
				@Override
				File call() {
					log.info "[Task COMPILE...]"
					def generatedFile = script.compile(analysisFile, factsDir, outDir, souffleOpts)
					log.info "[Task COMPILE Done]"
					return generatedFile
				}
			})
		}

		File generatedFile
		if (options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
			if (runInterpreted)
				log.info "Ignoring option --${options.X_SERIALIZE_FACTGEN_COMPILATION.name} when running in interpreted mode."
			else {
				generatedFile = compilationFuture.get()
				System.gc()
			}
		}

		try {
			if (options.X_SERVER_CHA.value) {
				log.info "[CHA...]"
				def methodLookupFile = new File("${Doop.souffleLogicPath}/addons/server-logic/method-lookup-ext.dl")
                if (runInterpreted) {
                    script.interpretScript(methodLookupFile, outDir, factsDir, souffleOpts)
                } else {
				    def generatedFile0 = script.compile(methodLookupFile, factsDir, outDir, souffleOpts)
				    script.run(generatedFile0, factsDir, outDir,
						       monitorInterval, monitorClosure, souffleOpts)
                }
				log.info "[CHA Done]"
			}

			if (options.FACTS_ONLY.value) return

			if (!analysisBinaryPath && !runInterpreted) {
				if (!options.X_SERIALIZE_FACTGEN_COMPILATION.value) {
					generatedFile = compilationFuture.get()
				}
				runtimeMetricsFile.append("analysis compilation time (sec)\t${script.compilationTime}\n")
			}

			if (!options.DRY_RUN.value) {
				if (runInterpreted) {
					script.interpretScript(analysisFile, outDir, factsDir, souffleOpts)
				} else {
				    File analysisBinary = analysisBinaryPath ? new File(analysisBinaryPath) : generatedFile
				    script.run(analysisBinary, factsDir, outDir,
						       monitorInterval, monitorClosure, souffleOpts)
				}

				runtimeMetricsFile.append("analysis execution time (sec)\t${script.executionTime}\n")
				int dbSize = (sizeOfDirectory(database) / 1024).intValue()
				runtimeMetricsFile.append("disk footprint (KB)\t${dbSize}\n")
			}
		} finally {
			executorService.shutdownNow()
		}
	}

	protected SouffleScript newScriptForAnalysis(Executor executor) {
		boolean viaDDlog = options.VIA_DDLOG.value as Boolean
		File cacheDir = new File(Doop.souffleAnalysesCache, name)
		return SouffleScript.newScript(executor, cacheDir, viaDDlog)
	}

	protected void postprocess() {
		super.postprocess()
		if (this.name == "xtractor") XTractor.run(this)
	}
}
