package org.clyze.doop.dynamicanalysis;

import com.google.common.collect.Lists;
import com.sun.tools.hat.internal.model.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by neville on 27/01/2017.
 */
public class DumpParsingUtil {
    private static final String[] UNKNOWN = new String[] {"Unknown"};
    public static final int BIG_NUMBER = 999;

    public static String[] convertType(String compact) {
        if (compact.length() == 0) return new String[]{"", ""};
        String first = compact.substring(0, 1);
        String res = null;
        if ("ZBCSIJFDV".contains(first)) {
            switch (first) {
                case "Z":
                    res = "boolean"; break;
                case "B":
                    res = "byte"; break;
                case "C":
                    res = "char"; break;
                case "S":
                    res = "short"; break;
                case "I":
                    res = "int"; break;
                case "J":
                    res = "long"; break;
                case "F":
                    res = "float"; break;
                case "D":
                    res = "double"; break;
                case "V":
                    res = "void"; break;
            }
            return new String[]{res, compact.substring(1)};
        } else {

                if (compact.startsWith("L")) {
                    String[] temp = compact.substring(1).split("\\;");
                    if (temp.length == 0)
                        throw new RuntimeException("Truncated input?");
                    res = temp[0].replace('/', '.');
                    String rest = String.join(";",  Arrays.copyOfRange(temp, 1, temp.length));
                    return new String[]{res, rest};
                } else if (compact.startsWith("[")) {
                    String[] temp = convertType(compact.substring(1));
                    return new String[]{temp[0]+ "[]", temp[1]};
                } else if (compact.startsWith("(")) {
                    String[] temp = compact.substring(1).split("\\)");
                    if (temp.length != 2)
                        throw new RuntimeException("Unknown: " + compact);
                    res = convertType(temp[1])[0] + " <MethodName>(";
                    String compactArgTypes = temp[0];
                    do {
                        temp = convertType(compactArgTypes);
                        compactArgTypes = temp[1];
                        res += temp[0] + ",";
                    } while (compactArgTypes.length() > 0);
                    return new String[] {res.substring(0, res.length()-1)+")", ""};

                } else throw new RuntimeException("Unknown: " + compact);
        }
    }

    static Snapshot getSnapshotFromFile(String filename) {

        File temp = new File(filename);
        Snapshot model = null;


        System.out.println("Reading from " + temp.getAbsolutePath() + "...");
        try {
            model = com.sun.tools.hat.internal.parser.Reader.readFile(temp.getAbsolutePath(), true, 0);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        System.out.println("Snapshot read, resolving...");
        model.resolve(true);
        System.out.println("Snapshot resolved.");


        return model;
    }

    static DynamicHeapAllocation getHeapRepresentation(StackTrace trace, JavaClass clazz) {
        if (trace != null && trace.getFrames().length > 0) {
            com.sun.tools.hat.internal.model.StackFrame[] frames = trace.getFrames();

            int lastRelevantIndex = BIG_NUMBER;
            if (clazz.isArray()) {
                lastRelevantIndex = 0;
            }
            else if (frames[frames.length-1].getClassName().equals("sun.reflect.NativeConstructorAccessorImpl") &&
                    frames[frames.length-1].getMethodName().equals("newInstance0")) {
                 // do nothing for now

            } else {
                for (int i = 0; i < trace.getFrames().length; i++) {
                    if (frames[i].getClassName().equals(clazz.getName()) && frames[i].getMethodName().equals("<init>"))
                        lastRelevantIndex = i + 1;
                }
            }



            // reverse order
            ArrayList<String> inMethods = new ArrayList<>();
            ArrayList<String> lineNumbers = new ArrayList<>();

           for (int i = 0; i<trace.getFrames().length && i <= lastRelevantIndex; i++) {
                com.sun.tools.hat.internal.model.StackFrame frame = trace.getFrames()[i];
                String fullyQualifiedMethodName = fullyQualifiedMethodSignatureFromFrame(frame);
                inMethods.add(fullyQualifiedMethodName);
                lineNumbers.add(frame.getLineNumber());
            }

            DynamicNormalHeapAllocation dyn = new DynamicNormalHeapAllocation(Lists.reverse(lineNumbers).toArray(new String[lineNumbers.size()]),
                    Lists.reverse(inMethods).toArray(new String[inMethods.size()]), clazz.getName());
           dyn.setProbablyUnmatched(lastRelevantIndex == BIG_NUMBER);
           return dyn;
        }
        return new DynamicNormalHeapAllocation(UNKNOWN, UNKNOWN, clazz.getName());
    }

    public static String fullyQualifiedMethodSignatureFromFrame(StackFrame frame) {
        return "<" + frame.getClassName() + ": " +  methodSignatureFromStackFrame(frame) + ">";
    }

    public static String methodSignatureFromStackFrame(StackFrame frame) {
        return convertType(frame.getMethodSignature())[0].replace("<MethodName>", frame.getMethodName());
    }

}
