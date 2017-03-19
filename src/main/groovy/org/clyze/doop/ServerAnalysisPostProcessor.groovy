package org.clyze.doop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.analysis.AnalysisPostProcessor
import org.clyze.doop.core.Doop;
import org.clyze.doop.core.DoopAnalysis;

class ServerAnalysisPostProcessor implements AnalysisPostProcessor<DoopAnalysis> {

    protected Log logger = LogFactory.getLog(getClass())

    @Override
    void process(DoopAnalysis analysis) {
        if (!analysis.options.X_STOP_AT_FACTS.value) {
            analysis.connector.addBlockFile("${Doop.addonsPath}/server-logic/queries.logic")
        }
    }
}
