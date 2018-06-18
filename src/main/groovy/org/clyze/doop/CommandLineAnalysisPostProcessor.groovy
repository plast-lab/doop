package org.clyze.doop

import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.AnalysisPostProcessor
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis

import java.nio.file.Files

class CommandLineAnalysisPostProcessor implements AnalysisPostProcessor<DoopAnalysis> {

	protected Log logger = LogFactory.getLog(getClass())

	@Override
	void process(DoopAnalysis analysis) {
		if (!analysis.options.X_SERVER_LOGIC.value && !analysis.options.X_STOP_AT_FACTS.value && !analysis.options.X_STOP_AT_INIT.value && !analysis.options.X_STOP_AT_BASIC.value)
			printStats(analysis)
		if (analysis.options.SANITY.value)
			printSanityResults(analysis)
		linkResult(analysis)
	}

	void printStats(DoopAnalysis analysis) {
		def lines = []

		if (analysis.options.LB3.value) {
			analysis.processRelation("Stats:Runtime") { String line ->
				if (!filterOutLBWarn(line)) lines << line
			}
		} else {
			def file = new File("${analysis.database}/Stats_Runtime.csv")
			file.eachLine { String line -> lines << line.replace("\t", ", ") }
		}

		logger.info "-- Runtime metrics --"
		lines.sort()*.split(", ").each {
			printf("%-80s %,d\n", it[0], it[1] as long)
		}

		if (!analysis.options.X_STATS_NONE.value) {
			lines = []

			if (analysis.options.LB3.value) {
				analysis.processRelation("Stats:Metrics") { String line ->
					if (!filterOutLBWarn(line)) lines.add(line)
				}
			} else {
				def file = new File("${analysis.database}/Stats_Metrics.csv")
				file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }
			}

			logger.info "-- Statistics --"
			lines.sort()*.split(", ").each {
				printf("%-80s %,d\n", it[1], it[2] as long)
			}
		}
	}

	void printSanityResults(DoopAnalysis analysis) {
		def file = new File("${analysis.database}/Sanity.csv")
		logger.info "-- Sanity Results --"
		file.readLines().sort()*.split("\t").each {
			printf("%-80s %,d\n", it[1], it[2] as long)
		}
	}

	void linkResult(DoopAnalysis analysis) {
		if (analysis.options.X_STOP_AT_FACTS.value) {
			logger.info "Making facts available at ${analysis.options.X_STOP_AT_FACTS.value}"
			return
		}

		def inputName
		def platform = analysis.options.PLATFORM.value

		if (analysis.options.X_START_AFTER_FACTS.value)
			inputName = analysis.id
		else
			inputName = FilenameUtils.getBaseName(analysis.inputFiles[0].toString())

		def humanDatabase = new File("${Doop.doopHome}/results/${inputName}/${analysis.name}/${platform}/${analysis.id}")
		humanDatabase.mkdirs()
		if (humanDatabase.exists()) humanDatabase.delete()
		logger.info "Making database available at $humanDatabase"
		Files.createSymbolicLink(humanDatabase.toPath(), analysis.database.toPath())

		def lastAnalysis = new File("${Doop.doopHome}/last-analysis")
		if (lastAnalysis.exists()) lastAnalysis.delete()
		logger.info "Making database available at $lastAnalysis"
		Files.createSymbolicLink(lastAnalysis.toPath(), analysis.database.toPath())

		if (analysis.options.SOUFFLE_PROFILE.value)
			logger.info "Souffle analysis profile available at ${analysis.outDir}/profile.txt"
	}

	static boolean filterOutLBWarn(String line) {
		line in ['*******************************************************************',
		         'Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.',
		         "Please use 'lb' instead of 'bloxbatch'."]
	}
}
