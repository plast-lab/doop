package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Triple;

import java.util.*;
import java.util.stream.Collectors;

public class ScalerRank {
	private final PointsToAnalysis pta;
//	private final ObjectAllocationGraph oag;
    private Set<Method> instanceMethods;
	private ContextComputer[] ctxComputers;
	private ContextComputer bottomLine;
	private Map<Method, Integer> ptsSize = new HashMap<>();
	/** Total Scalability Threshold */
	private long tst = 30000000;
	private List<Triple<Method, String, Integer>> results;

	public ScalerRank(PointsToAnalysis pta) {
		this.pta = pta;
	}

}
