package org.clyze.doop.ptatoolkit.scaler.analysis;

import Jama.Matrix;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.Set;

public class ScalerRank {
	private final PointsToAnalysis pta;

	public ScalerRank(PointsToAnalysis pta) {
		this.pta = pta;
	}

	public void rank() {
		//Creating  Arrays Representing Equations
		int totalMethods = pta.getMethodIdMap().keySet().size();
		double[][] lhsArray = new double[totalMethods][totalMethods];
		double[] rhsArray = new double[totalMethods];

		for (int i = 0; i < totalMethods; i++) {
			lhsArray[i][i] = 1.0;

			Method method = pta.getMethodIdMap().inverse().get(i);
			Set<Method> neighborSet = pta.getMethodNeighborMap().get(method);
			for (Method neighbor : neighborSet) {
				int neighborIndex = pta.getMethodIdMap().get(neighbor);
				lhsArray[i][neighborIndex] = -1.0/neighborSet.size();
			}

			rhsArray[i] = pta.getMethodTotalVPTMap().get(method);
		}
		//Creating Matrix Objects with arrays
		Matrix lhs = new Matrix(lhsArray);
		Matrix rhs = new Matrix(rhsArray, totalMethods);

		//Calculate Solved Matrix
		Matrix ans = lhs.solve(rhs);

		//Printing Answers
		for (int i = 0; i < totalMethods; i++) {
			System.out.println(pta.getMethodIdMap().inverse().get(i) + " answer: " + ans.get(i, 0));
		}
	}

}
