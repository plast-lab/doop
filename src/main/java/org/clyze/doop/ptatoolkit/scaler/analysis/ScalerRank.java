package org.clyze.doop.ptatoolkit.scaler.analysis;

import Jama.Matrix;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.*;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class ScalerRank {
	private final PointsToAnalysis pta;
	private Map<Method, Double> scores;
	/**
	 * Default number of maximum iterations.
	 */
	public static final int MAX_ITERATIONS_DEFAULT = 100000;

	/**
	 * Default value for the tolerance. The calculation will stop if the difference of PageRank
	 * values between iterations change less than this value.
	 */
	public static final double TOLERANCE_DEFAULT = 0.000000001;

	/**
	 * Damping factor default value.
	 */
	public static final double DAMPING_FACTOR_DEFAULT = 0.85d;

	public ScalerRank(PointsToAnalysis pta) {
		this.pta = pta;
	}

	public static boolean ASC = true;
	public static boolean DESC = false;

	public void rank() {
//		//Creating  Arrays Representing Equations
//		int totalMethods = pta.getMethodIdMap().keySet().size();
//		double[][] array = new double[totalMethods][totalMethods];
//		double[][] vector = new double[totalMethods][1];
//
//		for (int i = 0; i < totalMethods; i++) {
//			array[i][i] = 1.0;
//
//			Method method = pta.getMethodIdMap().inverse().get(i);
//			Set<Method> neighborSet = pta.getMethodNeighborMap().get(method);
//			if (neighborSet != null) {
//				for (Method neighbor : neighborSet) {
//					int neighborIndex = pta.getMethodIdMap().get(neighbor);
//					array[i][neighborIndex] = +1.0 / neighborSet.size();
//				}
//			}
//			else {
//				System.out.println("Warning -- " + method);
//			}
//
//			//vector[i] = pta.getMethodTotalVPTMap().get(method);
//			vector[i][0] = 1.0;
//		}
//
//		for (int n = 0; n < 10; n++) {
//			//Creating Matrix Objects with arrays
//			Matrix lhs = new Matrix(array);
//			Matrix rhs = new Matrix(vector);
//
//			//Calculate Solved Matrix
//			Matrix ans = lhs.times(rhs);
//
//			vector = ans.getArray();
//		}
//
//		//Printing Answers
//		System.out.println(Arrays.deepToString(vector));
		scores = new HashMap<>();
		int max_iterations = MAX_ITERATIONS_DEFAULT;
		// initialization
		int totalMethods = pta.getMethodIdMap().keySet().size();
		boolean weighted = false;
		Map<Method, Double> weights;
        if (weighted) {
			weights = new HashMap<>(totalMethods);
		} else {
			weights = Collections.emptyMap();
		}

		double initScore = 1.0d / totalMethods;
		for (Method m : pta.getMethodIdMap().keySet()) {
			scores.put(m, initScore);
//			if (weighted) {
//				double sum = 0;
//				for (E e : g.outgoingEdgesOf(m)) {
//					sum += g.getEdgeWeight(e);
//				}
//				weights.put(m, sum);
//			}
		}

		// run PageRank
		Map<Method, Double> nextScores = new HashMap<>();
		double maxChange = TOLERANCE_DEFAULT;

		while (max_iterations > 0 && maxChange >= TOLERANCE_DEFAULT) {
			// compute next iteration scores
			double r = 0d;
			for (Method m : pta.getMethodIdMap().keySet()) {
				if (pta.getMethodNeighborMap().get(m) != null && pta.getMethodNeighborMap().get(m).size() > 0) {
					r += (1d - DAMPING_FACTOR_DEFAULT) * scores.get(m);
				} else {
					r += scores.get(m);
				}
			}
			r /= totalMethods;

			maxChange = 0d;
			for (Method v : pta.getMethodIdMap().keySet()) {
				double contribution = 0d;

//				if (weighted) {
//					for (E e : g.incomingEdgesOf(v)) {
//						V w = Graphs.getOppositeVertex(g, e, v);
//						contribution +=
//								DAMPING_FACTOR_DEFAULT * scores.get(w) * g.getEdgeWeight(e) / weights.get(w);
//					}
//				} else {
				    if (pta.getMethodNeighborMap().get(v) != null) {
					    for (Method m : pta.getMethodNeighborMap().get(v)) {
						    //V w = Graphs.getOppositeVertex(g, e, v);
						    contribution += DAMPING_FACTOR_DEFAULT * scores.get(m) / pta.getMethodNeighborMap().get(m).size();
					    }
				    }
//				}

				double vOldValue = scores.get(v);
				double vNewValue = r + contribution;
				maxChange = Math.max(maxChange, Math.abs(vNewValue - vOldValue));
				nextScores.put(v, vNewValue);
			}

			// swap scores
			Map<Method, Double> tmp = scores;
			scores = nextScores;
			nextScores = tmp;

			// progress
			max_iterations--;
		}
		for (Method m : scores.keySet()) {
			if (pta.getMethodTotalVPTMap().get(m) != 0) {
				scores.put(m, scores.get(m)*pta.getMethodTotalVPTMap().get(m));
			}
		}

		System.out.println("After sorting ascending order......");
		// above code can be cleaned a bit by using method reference
		Map<Method, Double> sorted = scores
				.entrySet()
				.stream()
				.sorted(comparingByValue())
				.collect(
						toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
								LinkedHashMap::new));
		for (Method m : sorted.keySet()) {
			System.out.println("Method: " + m + " -> score: " + scores.get(m));
		}

	}
}
