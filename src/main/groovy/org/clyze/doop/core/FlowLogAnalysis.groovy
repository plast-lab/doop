package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.utils.FlowLogTransformer
import org.clyze.utils.CheckSum
import org.clyze.utils.Helper

import static org.apache.commons.io.FileUtils.sizeOfDirectory

@CompileStatic
@InheritConstructors
@Log4j
class FlowLogAnalysis extends SouffleCompatibleAnalysis {

	@Override
	protected void doRun(File analysisFile, File runtimeMetricsFile) {

		long monitorInterval = (options.X_MONITORING_INTERVAL.value as long) * 1000

		log.info "[Task COMPILE...]"
		File flowLogBinary = compileAnalysisFile(analysisFile, runtimeMetricsFile)
		log.debug("Compiled flowlog binary: ${flowLogBinary.canonicalPath}")
		log.info "[Task COMPILE Done]"

		log.info "[Task RUN...]"
		int workers = options.FLOWLOG_WORKERS.value as Integer ?: 4
		invokeFlowLogBinary(flowLogBinary, runtimeMetricsFile, monitorInterval, workers)
		log.info "[Task RUN Done]"

	}

	protected File compileAnalysisFile(File analysisFile, File runtimeMetricsFile) {

		String checksum = calcAnalysisFileChecksum(analysisFile)
		String flowLogBinaryName = getName() + "_" + checksum
		File flowLogBinaryDir = determineGeneratedBinaryTargetDir()
		File flowLogBinary = new File(flowLogBinaryDir, flowLogBinaryName)

		if (flowLogBinary.exists()) {
			// retrieve the file from the cache
			log.debug "Analysis compilation time (sec): 0"
			runtimeMetricsFile.append("analysis compilation time (sec)\t0\n")
			return flowLogBinary
		}

		File cargoTargetDir = determineCargoTargetDir()

		File flowLogBuildDir = new File(outDir, "flowlog_build")
		flowLogBuildDir.mkdirs()

		new FlowLogTransformer(analysisFile)
				.stripVarPrefixes()
				.dropPlanDirectives()
				.dropInlineQualifiers()
				.rewriteStatsMetrics()
				.writeTo(analysisFile)

		File db = new File(outDir, 'database')
		List<String> compilationCommandParts = List.of(
				options.FLOWLOG_COMPILER.value as String,
				"-F", factsDir.canonicalPath,
				"-D", db.canonicalPath,
				"-o", flowLogBinary.canonicalPath,
				"-B", flowLogBuildDir.canonicalPath,
				"-T", cargoTargetDir.canonicalPath,
				"--str-intern",
				//"--check"
				analysisFile.canonicalPath
		)
		String compilationCommand = compilationCommandParts.join(' ')
		log.debug "Compilation command ${compilationCommand}"

		File outFile = new File(outDir, "flowlog-compile.log")
		outFile.createNewFile()
		long executionTime = Helper.timing {
			executor.executeWithRedirectedOutput(compilationCommandParts, outFile) {
				log.debug it
			}
		}
		log.debug "Analysis compilation time (sec): $executionTime"
		runtimeMetricsFile.append("analysis compilation time (sec)\t${executionTime}\n")
		return flowLogBinary
	}

	protected String calcAnalysisFileChecksum(File analysisFile) {
		return CheckSum.checksum(analysisFile, DoopAnalysisFactory.HASH_ALGO)
	}

	protected File determineCargoTargetDir() {
		File cargoTargetDir = new File("${Doop.doopCache}/flowlog-analyses/cargo")
		cargoTargetDir.mkdirs()
		return cargoTargetDir
	}

	protected File determineGeneratedBinaryTargetDir() {
		File dir = new File("${Doop.doopCache}/flowlog-analyses/bin")
		dir.mkdirs()
		return dir
	}

	protected void invokeFlowLogBinary(File fileLogBinary, File runtimeMetricsFile, long monitorInterval, int workers) {
		File db = new File(outDir, 'database')

		List<String> executionCommandParts = List.of(
				fileLogBinary.canonicalPath,
				"-F", factsDir.canonicalPath,
				"-D", db.canonicalPath,
				"-w", workers.toString()
		)

		String executionCommand = executionCommandParts.join(' ')
		log.debug "Execution command ${executionCommand}"

		File outFile = new File(outDir, "flowlog-run.log")
		outFile.createNewFile()
		long executionTime = Helper.timing {
			executor.enableMonitor(monitorInterval, monitorClosure)
					.executeWithRedirectedOutput(executionCommandParts, outFile) {
						log.debug it
					}
					.disableMonitor()
		}
		log.debug "Analysis execution time (sec): $executionTime"
		runtimeMetricsFile.append("analysis execution time (sec)\t${executionTime}\n")

		int dbSize = (sizeOfDirectory(database) / 1024).intValue()
		runtimeMetricsFile.append("disk footprint (KB)\t${dbSize}\n")
	}

}
