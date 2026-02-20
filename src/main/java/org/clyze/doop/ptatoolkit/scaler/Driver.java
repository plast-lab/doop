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

/**
 * The Driver class serves as the entry point for running the Scaler analysis. It provides methods to execute the Scaler analysis using a specified facts directory and database, and to output the results of the analysis. The runScaler method initializes the DoopPointsToAnalysis with the given database, starts a timer for the analysis, creates a Scaler instance, and selects context sensitivities for each method. It also outputs method context sensitivities and type context sensitivities if debugging is enabled, and writes the Scaler results to a specified output file.
 */
public class Driver {

    private static final char SEP = '\t';
    private static final char EOL = '\n';

    /**
     * Runs the Scaler analysis using the provided facts directory and database. It initializes the DoopPointsToAnalysis with the given database, starts a timer for the analysis, and creates a Scaler instance with the points-to analysis and the output file for the results. The method then selects the context sensitivities for each method using the Scaler and outputs the results, including method context sensitivities and type context sensitivities. Finally, it writes the Scaler results to the specified output file.
     * @param factsDir the directory where the Scaler results will be written
     * @param database the database containing the points-to analysis results for the Scaler analysis
     * @throws FileNotFoundException
     */    
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

    /**
     * Outputs the method context information for each reachable method in the points-to analysis. The method sorts the reachable methods based on the number of contexts computed by the provided ContextComputer and prints the method name, number of contexts, and the product of the number of contexts and the accumulative points-to set size for each method. If the global setting is to list contexts, it also prints the number of contexts for each method.
     * @param pta the points-to analysis containing the reachable methods and their context information
     * @param cc the ContextComputer used to compute the number of contexts for each method
     * @param scaler the Scaler instance used to compute the accumulative points-to set size for each method
     */
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

    /**
     * Outputs the type context information for each reachable method in the points-to analysis. The method groups the reachable methods by their declaring types and calculates the total number of contexts for each type using the provided ContextComputer. The results are printed in descending order of the number of contexts, showing the type and its corresponding context count.
     * @param pta the points-to analysis containing the reachable methods and their declaring types
     * @param cc the ContextComputer used to compute the number of contexts for each method
     */
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

    /**
     * Writes the Scaler results to the specified output file. The results are organized by context sensitivity type, and each method is listed with its corresponding context sensitivity. The method groups the methods by their assigned context sensitivity and writes them to the output file in a sorted order based on their string representation.
     * @param results a map containing methods and their corresponding context sensitivity types
     * @param outputFile the file where the Scaler results will be written
     * @throws FileNotFoundException
     */
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
