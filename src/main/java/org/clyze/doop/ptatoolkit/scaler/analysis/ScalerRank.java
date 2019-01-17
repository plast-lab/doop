package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Triple;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class ScalerRank {
	private final PointsToAnalysis pta;
	private Map<Method, Double> scores;
	/**
	 * Default number of maximum iterations.
	 */
	private static final int MAX_ITERATIONS_DEFAULT = 1000;

	/**
	 * Default value for the tolerance. The calculation will stop if the difference of PageRank
	 * values between iterations change less than this value.
	 */
	private static final double TOLERANCE_DEFAULT = 0.000000001;

	/**
	 * Damping factor default value.
	 */
	private static final double DAMPING_FACTOR_DEFAULT = 0.0d;
	private final ObjectAllocationGraph oag;
	private Set<Method> reachableMethods;
	private ContextComputer[] ctxComputers;
	private ContextComputer bottomLine;
	private long tst = 900000000;
	private List<Triple<Method, String, Long>> results;

	public ScalerRank(PointsToAnalysis pta) {
		this.pta = pta;
		this.oag = new ObjectAllocationGraph(pta);
		init();
	}

	private void init() {

		// From the most precise analysis to the least precise analysis
//		ctxComputers = new ContextComputer[] {
//				new _2ObjectContextComputer(pta, oag),
//				new _2TypeContextComputer(pta, oag),
//				new _1TypeContextComputer(pta, oag),
//		};
//		bottomLine = new _InsensitiveContextComputer(pta);
//		reachableMethods = rank().stream()
//				.filter(Method::isInstance)
//				.collect(Collectors.toSet());
	}

	public static boolean ASC = true;
	public static boolean DESC = false;

	private Set<Method> rank() {
		scores = new HashMap<>();
		int max_iterations = MAX_ITERATIONS_DEFAULT;
		// initialization
		int totalMethods = pta.getMethodIdMap().keySet().size();
		for (Method m : pta.getMethodIdMap().keySet()) {
			double initScore = getFactor(m, ctxComputers[0]);
			scores.put(m, initScore);

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
				if (pta.getMethodNeighborMap().get(v) != null) {
					for (Method m : pta.getMethodNeighborMap().get(v)) {
						contribution += (DAMPING_FACTOR_DEFAULT * scores.get(m) * getFactor(m, ctxComputers[0]))/pta.getMethodNeighborMap().get(m).size();
					}
				}

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
				scores.put(m, scores.get(m)*getFactor(m, ctxComputers[0]));
			}
		}

		System.out.println("After sorting ascending order......");
		// above code can be cleaned a bit by using method reference
		Map<Method, Double> sorted = scores
				.entrySet()
				.stream()
				.sorted(comparingByValue())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		for (Method m : sorted.keySet()) {
			System.out.println("Method: " + m + " -> score: " + scores.get(m));
		}

		return sorted.keySet();
	}

	private int getAccumulativePTSSizeOf(Method method) {

		return pta.getMethodTotalVPTMap().get(method);
	}

	public Map<Method, String> selectContext() {
		if (Global.isDebug()) {
			results = new ArrayList<>();
		}
		System.out.println("Given TST value: " +
				ANSIColor.BOLD + ANSIColor.GREEN + tst + ANSIColor.RESET);
		long st = binarySearch(reachableMethods, tst);
		System.out.println("Selected ST value: " +
				ANSIColor.BOLD + ANSIColor.GREEN + st + ANSIColor.RESET);
		Map<Method, String> analysisMap = new HashMap<>();
		reachableMethods.forEach(method ->
				analysisMap.put(method, selectContextFor(method, st)));
		if (Global.isDebug()) {
			results.stream()
					.sorted(Comparator.comparing(Triple::getThird))
					.collect(Collectors.toCollection(LinkedList::new))
					.descendingIterator()
					.forEachRemaining(triple -> {
						Method method = triple.getFirst();
						String context = triple.getSecond();
						long nContexts = triple.getThird();
						long accumuPTSSize = getAccumulativePTSSizeOf(method);
						System.out.printf("#\t%s\t{%s}\t%d\t%d\n",
								method.toString(), context,
								nContexts, nContexts * accumuPTSSize);
					});
		}
		return analysisMap;
	}

	/**
	 *
	 * @param method
	 * @param st Scalability Threshold
	 * @return the analysis selected for method.
	 */
	private String selectContextFor(Method method, long st) {
		ContextComputer ctxComp = selectContext(method, st);
		if (Global.isDebug()) {
			results.add(new Triple<>(method,
					ctxComp.getAnalysisName(),
					ctxComp.contextNumberOf(method)));
		}
		return ctxComp.getAnalysisName();
	}

	/**
	 * Search the suitable tst such that the accumulative size
	 * of context-sensitive points to sets of given reachableMethods is less
	 * than given tst.
	 * @param methods
	 * @param tst Total Scalability Threshold
	 * @return the tst for every single method
	 */
	private long binarySearch(Set<Method> methods, long tst) {
		// Select the max value and make it as end
		long end = reachableMethods.stream()
				.mapToLong(m -> getFactor(m, ctxComputers[0]))
				.max()
				.getAsLong();
		long start = 0;
		long mid, ret = 0;
		while (start <= end) {
			mid = (start + end) / 2;
			long totalSize = getTotalAccumulativePTS(methods, mid);
			if (totalSize < tst) {
				ret = mid;
				start = mid + 1;
			} else if (totalSize > tst) {
				end = mid - 1;
			} else {
				ret = mid;
				break;
			}
		}
		return ret;
	}

	private long getFactor(Method method, ContextComputer cc) {
		return ((long) cc.contextNumberOf(method))
				* ((long) getAccumulativePTSSizeOf(method));
	}

	private long getTotalAccumulativePTS(Set<Method> methods,
	                                     long st) {
		long total = 0;
		for (Method method : methods) {
			if (!isSpecialMethod(method)) {
				ContextComputer cc = selectContext(method, st);
				total += getFactor(method, cc);
			}
		}
		return total;
	}

	/**
	 *
	 * @param method
	 * @param st Scalability Threshold
	 * @return the selected context computer for method according to tst
	 */
	private ContextComputer selectContext(Method method, long st) {
		ContextComputer ctxComp;
		if (isSpecialMethod(method)) {
			ctxComp = ctxComputers[0]; // the most precise analysis
		} else {
			ctxComp = bottomLine;
			for (ContextComputer cc : ctxComputers) {
				if (getFactor(method, cc) <= st) {
					ctxComp = cc;
					break;
				}
			}
		}
		return ctxComp;
	}

	private boolean isSpecialMethod(Method method) {
		return pta.declaringTypeOf(method).toString().startsWith("java.util.");
	}
}
