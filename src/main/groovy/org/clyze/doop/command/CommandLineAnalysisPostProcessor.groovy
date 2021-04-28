package org.clyze.doop.command

import groovy.util.logging.Log4j
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.analysis.AnalysisPostProcessor
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis
import org.clyze.utils.OS

import java.nio.file.Files
import java.nio.file.Path

@Log4j
class CommandLineAnalysisPostProcessor implements AnalysisPostProcessor<DoopAnalysis> {

	@Override
	void process(DoopAnalysis analysis) {
		if (!analysis.options.get('FACTS_ONLY').value)
			printStats(analysis)
		if (analysis.options.get('SANITY').value && !analysis.options.get('DRY_RUN').value)
			printSanityResults(analysis)
		linkResult(analysis)
	}

	void printStats(DoopAnalysis analysis) {
		def lines = []

		if (analysis.options.X_LB3.value) {
			analysis.processRelation("Stats:Runtime") { String line ->
				if (!filterOutLBWarn(line)) lines << line
			}
		} else {
			def file = new File(analysis.database, 'Stats_Runtime.csv')
			file.eachLine { String line -> lines << line.replace("\t", ", ") }
		}

		log.info "-- Runtime metrics --"
		lines.sort()*.split(", ").each {
			printf("%-80s %,d\n", it[0], it[1] as long)
		}

		if (!analysis.options.get('X_STATS_NONE').value && !analysis.options.get('DRY_RUN').value) {
			lines = []

			if (analysis.options.X_LB3.value) {
				analysis.processRelation("Stats:Metrics") { String line ->
					if (!filterOutLBWarn(line)) lines.add(line)
				}
			} else {
				def file = new File("${analysis.database}/Stats_Metrics.csv")
				file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }
			}

			log.info "-- Statistics --"
			lines.sort()*.split(", ").each {
				printf("%-80s %,d\n", it[1], it[2] as long)
			}
		}
	}

	void printSanityResults(DoopAnalysis analysis) {
		def file = new File("${analysis.database}/Sanity.csv")
		log.info "-- Sanity Results --"
		file.readLines().sort()*.split("\t").each {
			printf("%-80s %,d\n", it[1], it[2] as long)
		}
	}

	void linkResult(DoopAnalysis analysis) {
		def factsOnly = analysis.options.get('FACTS_ONLY').value
		if (factsOnly) {
			log.info "Making facts available at ${analysis.database}"
			return
		}

		def inputName
		def platform = analysis.options.get('PLATFORM').value

		if (analysis.options.get('INPUT_ID').value)
			inputName = analysis.id
		else
			inputName = FilenameUtils.getBaseName(analysis.inputFiles[0].toString())

		// Skip symbolic links on Windows.
		if (OS.win) {
			log.info "Making database available at $analysis.database"
		} else {
			def humanDatabase = new File("${Doop.doopHome}/results/${inputName}/${analysis.name}/${platform}/${analysis.id}")
			humanDatabase.mkdirs()
			if (humanDatabase.exists()) {
				FileUtils.deleteDirectory(humanDatabase)
			}
			log.info "Making database available at $humanDatabase"
			Path humanDatabasePath = humanDatabase.toPath()
			Files.deleteIfExists(humanDatabasePath)
			Files.createSymbolicLink(humanDatabasePath, analysis.database.toPath())

			def lastAnalysis = new File("${Doop.doopHome}/last-analysis")
			Path lastAnalysisPath = lastAnalysis.toPath()
			Files.deleteIfExists(lastAnalysisPath)
			log.info "Making database available at $lastAnalysis"
			Files.createSymbolicLink(lastAnalysisPath, analysis.database.toPath())
		}

		if (analysis.options.SOUFFLE_PROFILE.value)
			log.info "Souffle analysis profile available at ${analysis.outDir}/profile.txt"
	}

	static boolean filterOutLBWarn(String line) {
		line in ['*******************************************************************',
		         'Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.',
		         "Please use 'lb' instead of 'bloxbatch'."]
	}
}
