package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Triple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Given a TST (Total Scalability Threshold), select the ST (Scalability Threshold),
 * then select context-sensitivity based on the selected ST value.
 */
public class Scaler {

    private final DoopPointsToAnalysis pta;
    private final ObjectAllocationGraph oag;
    private Set<Method> reachableMethods;
    private ContextComputer[] ctxComputers;
    private ContextComputer bottomLine;
    private Map<Method, Integer> ptsSize = new HashMap<>();
    /** Total Scalability Threshold */
    private long tst = 500_000_000L;
    private List<Triple<Method, String, Long>> results;
    private File scalerOutput;

    public Scaler(DoopPointsToAnalysis pta, File scalerOutput) {
        this.pta = pta;
        this.oag = new ObjectAllocationGraph(pta);
        this.scalerOutput = scalerOutput;
        init();
    }

    public Map<Method, String> selectContext() throws FileNotFoundException {
        results = new ArrayList<>();
        System.out.println("Given TST value: " + ANSIColor.BOLD + ANSIColor.GREEN + tst + ANSIColor.RESET);
        long st = binarySearch(reachableMethods, tst);
        System.out.println("Selected ST value: " + ANSIColor.BOLD + ANSIColor.GREEN + st + ANSIColor.RESET);
        Map<Method, String> analysisMap = new HashMap<>();
        reachableMethods.forEach(method ->
                analysisMap.put(method, selectContextFor(method, st)));
        AtomicLong worstCaseVPT = new AtomicLong(0);
        AtomicLong numberOfMethods = new AtomicLong(0);

        final PrintWriter writer = new PrintWriter(scalerOutput);

        results.stream()
                .sorted(Comparator.comparing(Triple::getThird))
                .collect(Collectors.toCollection(LinkedList::new))
                .descendingIterator()
                .forEachRemaining(triple -> {
                    Method method = triple.getFirst();
                    String context = triple.getSecond();
                    long nContexts = triple.getThird();
                    long accumuPTSSize = getAccumulativePTSSizeOf(method);
                    writer.printf("%s\t%s\t%d\t%d\n",
                            method.toString(), context,
                            nContexts, nContexts * accumuPTSSize);
                    worstCaseVPT.getAndAdd(nContexts * accumuPTSSize);
                    numberOfMethods.getAndIncrement();
                });
        //}
        writer.close();
        System.out.println("Total worst case VPT: " + worstCaseVPT + " for " + numberOfMethods + " methods");
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

    public long getAccumulativePTSSizeOf(Method method) {

        return pta.getMethodTotalVPTMap().get(method);
    }

    private void init() {
        reachableMethods = pta.reachableMethods();
        System.out.println("Total Reachable Methods: " + reachableMethods.size());
        // From the most precise analysis to the least precise analysis

//        ContextComputer _2ObjectContextComputer = new _2ObjectContextComputer_ScalerPlus(pta, oag);
//        ctxComputers = new ContextComputer[] {
//                _2ObjectContextComputer,
//                new _2TypeContextComputer_ScalerPlus(pta, oag, _2ObjectContextComputer),
//                new _1TypeContextComputer_ScalerPlus(pta, oag, _2ObjectContextComputer),
//        };
        ContextComputer _2ObjectContextComputer = new _2ObjectContextComputer_Scaler(pta, oag);
        ctxComputers = new ContextComputer[] {
                _2ObjectContextComputer,
                new _2TypeContextComputer_Scaler(pta, oag),
                new _1TypeContextComputer_Scaler(pta, oag),
        };
        ctxComputers[0].computeContext();
        ctxComputers[1].computeContext();
        ctxComputers[2].computeContext();
        bottomLine = new _InsensitiveContextComputer(pta);
        bottomLine.computeContext();
    }

    /**
     *
     * @param method
     * @param st Scalability Threshold
     * @return the analysis selected for method.
     */
    private String selectContextFor(Method method, long st) {
        ContextComputer ctxComp = selectContext(method, st);
        //if (Global.isDebug()) {
            results.add(new Triple<>(method,
                    ctxComp.getAnalysisName(),
                    ctxComp.contextNumberOf(method)));
        //}
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
        return cc.contextNumberOf(method) * getAccumulativePTSSizeOf(method);
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
