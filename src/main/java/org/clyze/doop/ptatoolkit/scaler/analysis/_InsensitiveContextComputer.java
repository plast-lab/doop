package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.pta.basic.Method;

/**
 * Context-insensitive analysis can be seen as the analysis where
 * all contexts are merged as 1 context.
 */
public class _InsensitiveContextComputer extends ContextComputer {

    _InsensitiveContextComputer(DoopPointsToAnalysis pta) {
        super(pta, null);
    }

    @Override
    public String getAnalysisName() {
        return "context-insensitive";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        return 1;
    }
}
