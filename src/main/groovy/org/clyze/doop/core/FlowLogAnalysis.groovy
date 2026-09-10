package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Log4j
import org.clyze.doop.utils.FlowLogTransformer
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
		File flowLogBinary = compileAnalysisFile(analysisFile, runtimeMetricsFile, monitorInterval)
		log.debug("Compiled flowlog binary: ${flowLogBinary.canonicalPath}")
		log.info "[Task COMPILE Done]"

		log.info "[Task RUN...]"
		int workers = options.FLOWLOG_WORKERS.value as Integer ?: 4
		invokeFlowLogBinary(flowLogBinary, runtimeMetricsFile, monitorInterval, workers)
		log.info "[Task RUN Done]"

	}

	protected File compileAnalysisFile(File analysisFile, File runtimeMetricsFile, long monitorInterval) {
		String flowLogBinaryName = "flowlog_" + getName() + "_" + getId()
		File flowLogBinary = new File(outDir, flowLogBinaryName)

		File flowLogBuildDir = new File(outDir, "flowlog_build")
		flowLogBuildDir.mkdirs()

		FlowLogTransformer.stripVarPrefixes(analysisFile, analysisFile)
		File db = new File(outDir, 'database')
		List<String> compilationCommandParts = List.of(
				options.FLOWLOG_COMPILER.value as String,
				"-F", factsDir.canonicalPath,
				"-D", db.canonicalPath,
				"-o", flowLogBinary.canonicalPath,
				"-B", flowLogBuildDir.canonicalPath,
				"--str-intern",
				//"--check"
				analysisFile.canonicalPath
		)
		String compilationCommand = compilationCommandParts.join(' ')
		log.debug "Compilation command ${compilationCommand}"

		long executionTime = Helper.timing {
			executor.enableMonitor(monitorInterval, monitorClosure).execute(compilationCommandParts).disableMonitor()
		}
		log.debug "Analysis compilation time (sec): $executionTime"
		runtimeMetricsFile.append("analysis compilation time (sec)\t${executionTime}\n")
		return flowLogBinary
	}

	protected void invokeFlowLogBinary(File fileLogBinary, File runtimeMetricsFile, long monitorInterval, int workers) {
		List<String> executionCommandParts = List.of(
				fileLogBinary.canonicalPath,
				"-w", workers.toString()
		)

		String executionCommand = executionCommandParts.join(' ')
		log.debug "Execution command ${executionCommand}"

		long executionTime = Helper.timing {
			executor.enableMonitor(monitorInterval, monitorClosure).execute(executionCommandParts).disableMonitor()
		}
		log.debug "Analysis execution time (sec): $executionTime"
		runtimeMetricsFile.append("analysis execution time (sec)\t${executionTime}\n")

		int dbSize = (sizeOfDirectory(database) / 1024).intValue()
		runtimeMetricsFile.append("disk footprint (KB)\t${dbSize}\n")
	}

}
