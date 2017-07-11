package org.clyze.doop

import org.apache.commons.io.FilenameUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.clyze.analysis.Analysis
import org.clyze.analysis.AnalysisPostProcessor
import org.clyze.doop.core.Doop
import org.clyze.doop.core.DoopAnalysis

class CommandLineAnalysisPostProcessor implements AnalysisPostProcessor<DoopAnalysis> {

    protected Log logger = LogFactory.getLog(getClass())

    @Override
    void process(DoopAnalysis analysis) {
        if (!analysis.options.X_STOP_AT_FACTS.value && !analysis.options.X_STOP_AT_INIT.value && !analysis.options.X_STOP_AT_BASIC.value)
            printStats(analysis)
        if (analysis.options.SANITY.value)
            printSanityResults(analysis)
        linkResult(analysis)
    }


    protected void printStats(DoopAnalysis analysis) {
        def lines = []

        if (analysis.options.LB3.value) {
            analysis.processRelation("Stats:Runtime") { String line ->
                if (!filterOutLBWarn(line)) lines.add(line)
            }
        }
        else {
            def file = new File("${analysis.database}/Stats_Runtime.csv")
            file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }
        }

        logger.info "-- Runtime metrics --"
        lines.sort()*.split(", ").each {
            printf("%-80s %,d\n", it[0], it[1] as long)
        }

        if (!analysis.options.X_STATS_NONE.value) {
            lines = []

            if (analysis.options.LB3.value) {
                analysis.processRelation("Stats:Runtime") { String line ->
                    if (!filterOutLBWarn(line)) lines.add(line)
                }
            }
            else {
                def file = new File("${analysis.database}/Stats_Metrics.csv")
                file.eachLine { String line -> lines.add(line.replace("\t", ", ")) }
            }

            logger.info "-- Statistics --"
            for (it in lines.sort()*.split(", ")) {
                printf("%-80s %,d\n", it[0], it[1] as long)
            }
        }
    }

    protected void printSanityResults(Analysis analysis) {
        def file = new File("${analysis.database}/Sanity.csv")
        logger.info "-- Sanity Results --"
        file.readLines().sort()*.split("\t").each {
            printf("%-80s %,d\n", it[1], it[2] as long)
        }
    }

    protected void linkResult(Analysis analysis) {
        if (analysis.options.X_STOP_AT_FACTS.value) {
            def facts = new File(analysis.options.X_STOP_AT_FACTS.value)
            logger.info "Making facts available at $facts"
            analysis.executor.execute("ln -s -f ${analysis.factsDir} \"$facts\"")
            return
        }

        def platform = analysis.options.PLATFORM.value
        def inputName = FilenameUtils.getBaseName(analysis.inputFiles[0].toString())

        def humanDatabase = new File("${Doop.doopHome}/results/${inputName}/${analysis.name}/${platform}/${analysis.id}")
        humanDatabase.mkdirs()
        logger.info "Making database available at $humanDatabase"
        analysis.executor.execute("ln -s -f ${analysis.database} \"$humanDatabase\"")

        def lastAnalysis = "${Doop.doopHome}/last-analysis"
        logger.info "Making database available at $lastAnalysis"
        analysis.executor.execute("ln -s -f -n ${analysis.database} \"$lastAnalysis\"")
    }

    protected boolean filterOutLBWarn(String line) {
        return line == '*******************************************************************' ||
            line == 'Warning: BloxBatch is deprecated and will not be supported in LogicBlox 4.0.' ||
            line == "Please use 'lb' instead of 'bloxbatch'."
    }
}
