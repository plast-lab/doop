package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Triple;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Given a TST (Total Scalability Threshold), select the ST (Scalability Threshold),
 * then select context-sensitivity based on the selected ST value.
 */
public class Scaler {

    private final PointsToAnalysis pta;
    private final ObjectAllocationGraph oag;
    private Set<Method> instanceMethods;
    private ContextComputer[] ctxComputers;
    private ContextComputer bottomLine;
    private Map<Method, Integer> ptsSize = new HashMap<>();
    /** Total Scalability Threshold */
    private long tst = 30000000;
    private List<Triple<Method, String, Integer>> results;

    public Scaler(PointsToAnalysis pta) {
        this.pta = pta;
        this.oag = new ObjectAllocationGraph(pta);
        init();
    }

    public Map<Method, String> selectContext() {
        if (Global.isDebug()) {
            results = new ArrayList<>();
        }
        System.out.println("Given TST value: " +
                ANSIColor.BOLD + ANSIColor.GREEN + tst + ANSIColor.RESET);
        long st = binarySearch(instanceMethods, tst);
        System.out.println("Selected ST value: " +
                ANSIColor.BOLD + ANSIColor.GREEN + st + ANSIColor.RESET);
        Map<Method, String> analysisMap = new HashMap<>();
        instanceMethods.forEach(method ->
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
                        System.out.printf("# %s\t{%s}\t%d\t%d\n",
                                method.toString(), context,
                                nContexts, nContexts * accumuPTSSize);
                    });
        }
        return analysisMap;
    }

    public ContextComputer[] getContextComputers() {
        return ctxComputers;
    }

    public long getTST() {
        return tst;
    }

    public void setTST(long tst) {
        this.tst = tst;
    }

    public int getAccumulativePTSSizeOf(Method method) {
        if (!ptsSize.containsKey(method)) {
            int size = pta.variablesDeclaredIn(method)
                    .stream()
                    .mapToInt(pta::pointsToSetSizeOf)
                    .sum();
            ptsSize.put(method, size);
        }
        return ptsSize.get(method);
    }

    private void init() {
        instanceMethods = pta.reachableMethods()
                .stream()
                .filter(Method::isInstance)
                .collect(Collectors.toSet());
        // From the most precise analysis to the least precise analysis
        ctxComputers = new ContextComputer[] {
                new _2ObjectContextComputer(pta, oag),
                new _2TypeContextComputer(pta, oag),
                new _1TypeContextComputer(pta, oag),
        };
        bottomLine = new _InsensitiveContextComputer(pta);
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
     * of context-sensitive points to sets of given instanceMethods is less
     * than given tst.
     * @param methods
     * @param tst Total Scalability Threshold
     * @return the tst for every single method
     */
    private long binarySearch(Set<Method> methods, long tst) {
        // Select the max value and make it as end
        long end = instanceMethods.stream()
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
