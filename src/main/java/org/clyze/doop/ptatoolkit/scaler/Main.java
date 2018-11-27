package org.clyze.doop.ptatoolkit.scaler;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.Options;
import org.clyze.doop.ptatoolkit.scaler.analysis.ContextComputer;
import org.clyze.doop.ptatoolkit.scaler.analysis.Scaler;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.ANSIColor;
import org.clyze.doop.ptatoolkit.util.Timer;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final char SEP = '\t';
    private static final char EOL = '\n';

    public static void main(String[] args) throws FileNotFoundException {
        Options opt = Options.parse(args);
        run(opt);
    }

    public static void run(Options opt) throws FileNotFoundException {
        System.out.printf("Analyze %s ...\n", opt.getApp());
        PointsToAnalysis pta = readPointsToAnalysis(opt);
        if (Global.isDebug()) {
            System.out.printf("%d objects in (pre) points-to analysis.\n",
                    pta.allObjects().size());
        }

        Timer scalerTimer = new Timer("Scaler Timer");
        System.out.println(ANSIColor.BOLD + ANSIColor.YELLOW + "Scaler starts ..." + ANSIColor.RESET);
        scalerTimer.start();
        Scaler scaler = new Scaler(pta);
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

        File scalerOutput = new File(opt.getOutPath(),
                String.format("%s-ScalerMethodContext-TST%d.facts",
                opt.getApp(), scaler.getTST()));
        System.out.printf("Writing Scaler method context sensitivities to %s...\n",
                scalerOutput.getPath());
        writeScalerResults(scalerResults, scalerOutput);
    }

    public static void runInsideDoop(File factsDir, File database) throws FileNotFoundException {
//        System.out.printf("Analyze %s ...\n", opt.getApp());
        PointsToAnalysis pta = new DoopPointsToAnalysis(database);
        if (Global.isDebug()) {
            System.out.printf("%d objects in (pre) points-to analysis.\n",
                    pta.allObjects().size());
        }

        Timer scalerTimer = new Timer("Scaler Timer");
        System.out.println(ANSIColor.BOLD + ANSIColor.YELLOW + "Scaler starts ..." + ANSIColor.RESET);
        scalerTimer.start();
        Scaler scaler = new Scaler(pta);
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

        File scalerOutput = new File(factsDir, "SpecialContextSensitivityMethod.facts");
        System.out.printf("Writing Scaler method context sensitivities to %s...\n",
                scalerOutput.getPath());
        writeScalerResults(scalerResults, scalerOutput);
    }

    public static PointsToAnalysis readPointsToAnalysis(Options opt) {
        try {
            Class ptaClass = Class.forName(opt.getPTA());
            Constructor constructor = ptaClass.getConstructor(Options.class);
            return (PointsToAnalysis) constructor.newInstance(opt);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("Reading points-to analysis results fails");
        }
    }

    public static void outputMethodContext(PointsToAnalysis pta,
                                           ContextComputer cc,
                                           Scaler scaler) {
        System.out.println("Method context, analysis: " + cc.getAnalysisName());
        pta.reachableMethods().stream()
                .filter(Method::isInstance)
                .sorted((m1, m2) -> cc.contextNumberOf(m2) - cc.contextNumberOf(m1))
                .forEach(m -> {
                    System.out.printf("%s\t%d\tcontexts\t%d ",
                            m.toString(), cc.contextNumberOf(m),
                            ((long) cc.contextNumberOf(m))
                                    * ((long) scaler.getAccumulativePTSSizeOf(m)));
                    if (Global.isListContext()) {
                        System.out.print(cc.contextNumberOf(m));
                    }
                    System.out.println();
                });
    }

    public static void outputContextByType(PointsToAnalysis pta,
                                           ContextComputer cc) {
        System.out.println("Type context, analysis: " + cc.getAnalysisName());
        Map<Type, List<Method>> group = pta.reachableMethods().stream()
                .filter(Method::isInstance)
                .collect(Collectors.groupingBy(pta::declaringTypeOf));
        Map<Type, Integer> typeContext = new HashMap<>();
        group.forEach((type, methods) -> {
            int contextSum = methods.stream()
                    .mapToInt(m -> cc.contextNumberOf(m))
                    .sum();
            typeContext.put(type, contextSum);
        });
        typeContext.entrySet()
                .stream()
                .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                .forEach(e -> System.out.printf("%s: %d contexts\n",
                        e.getKey(), e.getValue()));
    }

    private static void writeScalerResults(
            Map<Method, String> results, File outputFile)
            throws FileNotFoundException {
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
