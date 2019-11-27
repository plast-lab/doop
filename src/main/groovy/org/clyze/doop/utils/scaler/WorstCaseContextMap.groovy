package org.clyze.doop.utils.scaler

class AnalysisWorstCaseContextCounter {

	private String analysisName
	private Map<String, Long> worstCaseContextMap

	AnalysisWorstCaseContextCounter(String analysisName, Map<String, Long> worstCaseContextMap) {
		this.analysisName = analysisName
		this.worstCaseContextMap = worstCaseContextMap
	}

	String getAnalysisName() {
		return this.analysisName
	}

	Long numberOfContextsForMethod(String method) {
		return worstCaseContextMap.getOrDefault(method, 1)
	}
}
