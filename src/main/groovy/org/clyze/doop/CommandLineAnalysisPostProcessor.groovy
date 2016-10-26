package org.clyze.doop

import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.doop.core.*

class CommandLineAnalysisPostProcessor implements AnalysisPostProcessor {

    protected Log logger = LogFactory.getLog(getClass())

    @Override
    void process(Analysis analysis) {
        if (!analysis.options.X_STOP_AT_FACTS.value && !analysis.options.X_STOP_AT_INIT.value && !analysis.options.X_STOP_AT_BASIC.value)
            printStats(analysis)
        linkResult(analysis)
    }


    protected void printStats(Analysis analysis) {
        def lines = [] as List<String>
        analysis.connector.processPredicate("Stats:Runtime") { String line ->
            lines.add(line)
        }

        logger.info "-- Runtime metrics --"
        lines.sort()*.split(", ").each {
            printf("%-80s %,.2f\n", it[0], it[1] as float)
        }

        if (!analysis.options.X_STATS_NONE.value) {
            lines = [] as List<String>
            analysis.connector.processPredicate("Stats:Metrics") { String line ->
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

    protected void linkResult(Analysis analysis) {
        if (analysis.options.X_STOP_AT_FACTS.value) {
            def facts = new File(analysis.options.X_STOP_AT_FACTS.value)
            logger.info "Making facts available at $facts"
            analysis.executor.execute("ln -s -f ${analysis.facts} \"$facts\"")
            return
        }

        def platform = analysis.options.PLATFORM.value
        def inputName = FilenameUtils.getBaseName(analysis.inputs[0].toString())

        def humanDatabase = new File("${Doop.doopHome}/results/${inputName}/${analysis.name}/${platform}/${analysis.id}")
        humanDatabase.mkdirs()
        logger.info "Making database available at $humanDatabase"
        analysis.executor.execute("ln -s -f ${analysis.database} \"$humanDatabase\"")

        def lastAnalysis = "${Doop.doopHome}/last-analysis"
        logger.info "Making database available at $lastAnalysis"
        analysis.executor.execute("ln -s -f -n ${analysis.database} \"$lastAnalysis\"")
    }
}
