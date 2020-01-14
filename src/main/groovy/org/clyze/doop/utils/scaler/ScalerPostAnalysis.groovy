package org.clyze.doop.utils.scaler


import org.clyze.doop.ptatoolkit.util.ANSIColor
import org.clyze.doop.ptatoolkit.util.Timer
import org.clyze.doop.ptatoolkit.util.Triple

import java.util.stream.Collectors

class ScalerPostAnalysis {
	private static final char SEP = '\t';
	private static final char EOL = '\n';

	private File methodTotalVPTFile
	private File methodAllPossibleTwoObjectContextsFile
	private File methodAllPossibleTwoTypeContextsFile
	private File methodAllPossibleOneTypeContextsFile
	private File reachableMethodsFile
	private File insensitiveMethodsFIle
	private File variableCantBenefitFromCSFile
	private File stickyContextMethodsFile
	private Map<String, Long> methodInsVPTSizeMap = [:]
	private AnalysisWorstCaseContextCounter[] contextCounters = new AnalysisWorstCaseContextCounter[4]
	private long tst = 20_000_000L
	private List<Triple<String, String, Long>> results
	private Set<String> reachableMethods
	private Set<String> noCSMethods
	private Set<String> noCSVariables
	private Set<String> stickyContextMethods

	ScalerPostAnalysis(File database) {
		methodTotalVPTFile = new File(database.getAbsolutePath() + File.separator + "Method_TotalVPT.csv")
		insensitiveMethodsFIle = new File(database.getAbsolutePath() + File.separator + "InsensitiveMethod.csv")
		stickyContextMethodsFile = new File(database.getAbsolutePath() + File.separator + "StickyContextMethod.csv")
		variableCantBenefitFromCSFile = new File(database.getAbsolutePath() + File.separator + "VariableCantBenefitFromCS.csv")
		methodAllPossibleTwoObjectContextsFile = new File(database.getAbsolutePath() + File.separator + "mainAnalysis.Method_AllPossibleTwoObjContexts.csv")
		methodAllPossibleTwoTypeContextsFile = new File(database.getAbsolutePath() + File.separator + "mainAnalysis.Method_AllPossibleTwoTypeContexts.csv")
		methodAllPossibleOneTypeContextsFile = new File(database.getAbsolutePath() + File.separator + "mainAnalysis.Method_AllPossibleOneTypeContexts.csv")
		reachableMethodsFile = new File(database.getAbsolutePath() + File.separator + "Reachable.csv")
		methodInsVPTSizeMap = [:]

		buildMethodTotalVPTSize()
		buildMethodMaxTwoObjectContexts()
		buildMethodMaxTwoTypeContexts()
		buildMethodMaxOneTypeContexts()
		buildMethodMaxInsensitiveContexts()
		buildReachableMethods()
		//buildNoCSMethods()
		//buildStickyContextMethods()
		//buildNoCSVariables()
	}

	void buildMethodMaxTwoObjectContexts() {
		Map<String, Long> contextCounter = [:]
		methodAllPossibleTwoObjectContextsFile.eachLine { line ->
			String[] pieces = line.split("\t")
			def method = pieces[0]

			if (contextCounter.containsKey(method)) {
				contextCounter.put(method, contextCounter.get(method)  + 1)
			}
			else {
				contextCounter.put(method, 1L)
			}
		}

		contextCounters[0] = new AnalysisWorstCaseContextCounter("2-object", contextCounter)
	}

	void buildMethodMaxTwoTypeContexts() {
		Map<String, Long> contextCounter = [:]

		methodAllPossibleTwoTypeContextsFile.eachLine { line ->
			String[] pieces = line.split("\t")
			def method = pieces[0]

			if (contextCounter.containsKey(method)) {
				contextCounter.put(method, contextCounter.get(method)  + 1)
			}
			else {
				contextCounter.put(method, 1L)
			}
		}

		contextCounters[1] = new AnalysisWorstCaseContextCounter("2-type", contextCounter)
	}

	void buildMethodMaxOneTypeContexts() {
		Map<String, Long> contextCounter = [:]

		methodAllPossibleOneTypeContextsFile.eachLine { line ->
			String[] pieces = line.split("\t")
			def method = pieces[0]

			if (contextCounter.containsKey(method)) {
				contextCounter.put(method, contextCounter.get(method)  + 1)
			}
			else {
				contextCounter.put(method, 1L)
			}
		}
		contextCounters[2] = new AnalysisWorstCaseContextCounter("1-type", contextCounter)
	}

	void buildMethodMaxInsensitiveContexts() {
		Map<String, Long> contextCounter = [:]

		for (String method : reachableMethods) {
			contextCounter.put(method, 1L)
		}
		contextCounters[3] = new AnalysisWorstCaseContextCounter("context-insensitive", contextCounter)
	}

	void buildReachableMethods() {
		reachableMethods = []

		reachableMethodsFile.eachLine {line ->
			reachableMethods.add(line)
		}
	}

	void buildNoCSMethods() {
		noCSMethods = []

		insensitiveMethodsFIle.eachLine { line ->
			noCSMethods.add(line)
		}
	}

//	void buildStickyContextMethods() {
//		stickyContextMethods = []
//
//		stickyContextMethodsFile.eachLine { line ->
//			stickyContextMethods.add(line)
//		}
//	}
//	void buildNoCSVariables() {
//		noCSVariables = []
//
//		variableCantBenefitFromCSFile.eachLine {line ->
//			noCSVariables.add(line)
//		}
//	}

	void buildMethodTotalVPTSize() {
		methodTotalVPTFile.eachLine { line ->
			String[] pieces = line.split("\t")
			def method = pieces[0]
			def totalVPTSize = pieces[1]

			methodInsVPTSizeMap.put(method, Long.parseLong(totalVPTSize))
		}
	}

	void run(File factsDir) throws FileNotFoundException {
		Timer scalerTimer = new Timer("Scaler Timer")
		System.out.println(ANSIColor.BOLD + ANSIColor.YELLOW + "Scaler starts ..." + ANSIColor.RESET)
		scalerTimer.start()
		File scalerOutput = new File(factsDir, "SpecialContextSensitivityMethod.facts")
		File insensitiveVariableOutput = new File(factsDir, "InsensitiveVar.facts")

		selectContexts(scalerOutput)

		scalerTimer.stop()
		System.out.print(ANSIColor.BOLD + ANSIColor.YELLOW +
				"Scaler finishes, analysis time: " + ANSIColor.RESET)
		System.out.print(ANSIColor.BOLD + ANSIColor.GREEN)
		System.out.printf("%.2fs", scalerTimer.inSecond())
		System.out.println(ANSIColor.RESET)
	}

	void selectContexts(File scalerOutput) throws FileNotFoundException {
		results = new ArrayList<>()
		System.out.println("Given TST value: " + ANSIColor.BOLD + ANSIColor.GREEN + tst + ANSIColor.RESET)
		long st = binarySearch(tst)
		System.out.println("Selected ST value: " + ANSIColor.BOLD + ANSIColor.GREEN + st + ANSIColor.RESET)

		reachableMethods.forEach({ method ->
			finalizeContextSelection(method, st)
		})
		long worstCaseVPT = 0L
		long numberOfMethods = 0L

		final PrintWriter writer = new PrintWriter(scalerOutput)

		results.stream()
				.sorted(Comparator.comparing({ it.getThird() }))
				.collect(Collectors.toCollection({ new LinkedList() }))
				.descendingIterator()
				.forEachRemaining({ triple ->
					String method = triple.getFirst() as String
					String context = triple.getSecond() as String
					long nContexts = triple.getThird() as long
					long methodInsVPTSize = methodInsVPTSizeMap.get(method)
					writer.printf("%s\t%s\t%d\t%d\n",
							method.toString(), context,
							nContexts, nContexts * methodInsVPTSize)
					worstCaseVPT += nContexts * methodInsVPTSize
					numberOfMethods += 1
				})

		writer.close()
		System.out.println("Total worst case VPT: " + worstCaseVPT + " for " + numberOfMethods + " methods")
	}

	/**
	 *
	 * @param method
	 * @param st Scalability Threshold
	 * @return the analysis selected for method.
	 */
	private String finalizeContextSelection(String method, long st) {
		AnalysisWorstCaseContextCounter ctxCounter

		if (isSpecialMethod(method)) {
			ctxCounter = contextCounters[0]; // the most precise analysis
		}
		else {
			ctxCounter = contextCounters[0]
			for (AnalysisWorstCaseContextCounter selectedContextCounter : contextCounters) {
				if (getFactor(method, selectedContextCounter) <= st) {
					ctxCounter = selectedContextCounter
					break
				}
			}
		}

		results.add(new Triple<>(method,
				ctxCounter.getAnalysisName() ,
				ctxCounter.numberOfContextsForMethod(method)));

		return ctxCounter.getAnalysisName();
	}

	private long binarySearch(long tst) {
		// Select the max value and make it as end
		long end = reachableMethods.stream()
				.mapToLong({ m -> getFactor(m, contextCounters[0]) })
				.max()
				.getAsLong()
		long start = 0L
		long mid, ret = 0L
		while (start <= end) {
			mid = ((start + end) / 2) as Long
			long totalSize = getTotalAccumulativePTS(mid)
			if (totalSize < tst) {
				ret = mid
				start = mid + 1
			} else if (totalSize > tst) {
				end = mid - 1
			} else {
				ret = mid
				break
			}
		}
		return ret
	}

	private long getFactor(String method, AnalysisWorstCaseContextCounter ctxCounter) {
		return ctxCounter.numberOfContextsForMethod(method) * methodInsVPTSizeMap.get(method)
	}

	private long getTotalAccumulativePTS(long st) {
		long total = 0
		for (String method : reachableMethods) {
			if (!isSpecialMethod(method)) {
				AnalysisWorstCaseContextCounter contextMap = selectContextForMethod(method, st)
				total += getFactor(method, contextMap)
			}
			else {
				total += 0
			}
		}
		return total
	}

	/**
	 *
	 * @param method
	 * @param st Scalability Threshold
	 * @return the selected context computer for method according to tst
	 */
	private AnalysisWorstCaseContextCounter selectContextForMethod(String method, long st) {
		AnalysisWorstCaseContextCounter ctxCounter
		if (isSpecialMethod(method)) {
			ctxCounter = contextCounters[0]; // the most precise analysis
		}
		else {
			ctxCounter = contextCounters[3]
			for (AnalysisWorstCaseContextCounter selectedContextCounter : contextCounters) {
				if (getFactor(method, selectedContextCounter) <= st) {
					ctxCounter = selectedContextCounter
					break
				}
			}
		}
		return ctxCounter
	}

	private boolean isSpecialMethod(String method) {
		method.startsWith("<java.util.")
	}
}
