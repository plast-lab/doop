package org.clyze.doop.ptatoolkit.scaler;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.analysis.ContextComputer;
import org.clyze.doop.ptatoolkit.scaler.analysis.Scaler;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Timer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class Driver {

    private static final char SEP = '\t';
    private static final char EOL = '\n';


    public static void runScaler(File factsDir, File database) throws FileNotFoundException {
        DoopPointsToAnalysis pta = new DoopPointsToAnalysis(database, "scaler");
        if (Global.isDebug()) {
            System.out.printf("%d objects in (pre) points-to analysis.\n",
                    pta.allObjects().size());
        }

        Timer scalerTimer = new Timer("Scaler Timer");
        System.out.println(ANSIColor.BOLD + ANSIColor.YELLOW + "Scaler starts ..." + ANSIColor.RESET);
        scalerTimer.start();
        File scalerOutput = new File(factsDir, "SpecialContextSensitivityMethod.facts");

        Scaler scaler = new Scaler(pta, scalerOutput);

        if (Global.getTST() != Global.UNDEFINE) {
            scaler.setTST(Global.getTST());
        }
        Map<Method, String> scalerResults = scaler.selectContext();
        scalerTimer.stop();
        System.out.print(ANSIColor.BOLD + ANSIColor.YELLOW +
                "Scaler finishes, analysis time: " + ANSIColor.RESET);
        System.out.print(ANSIColor.BOLD + ANSIColor.GREEN);
        System.out.printf("%.2fs", scalerTimer.inSecond());
        System.out.println(ANSIColor.RESET);
        if (Global.isDebug()) {
            for (ContextComputer cc : scaler.getContextComputers()) {
                outputMethodContext(pta, cc, scaler);
                outputContextByType(pta, cc);
            }
        }

        System.out.printf("Writing Scaler method context sensitivities to %s...\n",
                scalerOutput.getPath());

        //writeScalerResults(scalerResults, scalerOutput);
    }

//    public static void runScalerRank(File factsDir, File database) throws FileNotFoundException {
//        PointsToAnalysis pta = new DoopPointsToAnalysis(database, "scalerRank");
//        if (Global.isDebug()) {
//            System.out.printf("%d objects in (pre) points-to analysis.\n",
//                    pta.allObjects().size());
//        }
//
//        Timer scalerTimer = new Timer("Scaler Timer");
//        System.out.println(ANSIColor.BOLD + ANSIColor.YELLOW + "Scaler Rank starts ..." + ANSIColor.RESET);
//        scalerTimer.start();
//        //Scaler scalerRank = new ScalerRank(pta);
//
//        scalerTimer.stop();
//        System.out.print(ANSIColor.BOLD + ANSIColor.YELLOW +
//                "Scaler finishes, analysis time: " + ANSIColor.RESET);
//        System.out.print(ANSIColor.BOLD + ANSIColor.GREEN);
//        System.out.printf("%.2fs", scalerTimer.inSecond());
//        System.out.println(ANSIColor.RESET);
//
//
//        //Map<Method, String> scalerResults = scalerRank.selectContext();
//
//        File scalerOutput = new File(factsDir, "SpecialContextSensitivityMethod.facts");
//        System.out.printf("Writing Scaler method context sensitivities to %s...\n",
//                scalerOutput.getPath());
//        writeScalerResults(scalerResults, scalerOutput);
//    }

    private static void outputMethodContext(PointsToAnalysis pta, ContextComputer cc, Scaler scaler) {
        System.out.println("Method context, analysis: " + cc.getAnalysisName());
        pta.reachableMethods().stream()
                .sorted((m1, m2) -> Long.compare(cc.contextNumberOf(m2), cc.contextNumberOf(m1)))
                .forEach(m -> {
                    System.out.printf("%s\t%ld\tcontexts\t%ld ",
                            m.toString(), cc.contextNumberOf(m),
                            cc.contextNumberOf(m)
                                    * scaler.getAccumulativePTSSizeOf(m));
                    if (Global.isListContext()) {
                        System.out.print(cc.contextNumberOf(m));
                    }
                    System.out.println();
                });
    }

    private static void outputContextByType(PointsToAnalysis pta, ContextComputer cc) {
        System.out.println("Type context, analysis: " + cc.getAnalysisName());
        Map<Type, List<Method>> group = pta.reachableMethods().stream()
                .collect(Collectors.groupingBy(pta::declaringTypeOf));
        Map<Type, Long> typeContext = new HashMap<>();
        group.forEach((type, methods) -> {
            long contextSum = methods.stream()
                    .mapToLong(cc::contextNumberOf)
                    .sum();
            typeContext.put(type, contextSum);
        });
        typeContext.entrySet()
                .stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .forEach(e -> System.out.printf("%s: %ld contexts\n",
                        e.getKey(), e.getValue()));
    }

    private static void writeScalerResults(Map<Method, String> results, File outputFile) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(outputFile);
        String[] CS = { "context-insensitive", "1-type", "2-type", "2-object" };
        Map<String, Set<Method>> contextMethods = new HashMap<>();
        for (String cs : CS) {
            contextMethods.put(cs, new HashSet<>());
        }
        results.forEach((m, cs) -> {
            contextMethods.get(cs).add(m);
        });
        for (String cs : CS) {
            contextMethods.get(cs).stream()
                    .sorted(Comparator.comparing(Method::toString))
                    .forEach(method -> {
                        writer.write(method.toString());
                        writer.write(SEP);
                        writer.write(cs);
                        writer.write(EOL);
                    });
        }
        writer.close();
    }
}
