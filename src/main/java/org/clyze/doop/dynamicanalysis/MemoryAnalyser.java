package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import org.clyze.doop.common.Database;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.io.*;
import java.util.*;

// Z                        boolean
// B                        byte
// C                        char
// S                        short
// I                        int
// J                        long
// F                        float
// D                        double
// L fully-qualified-class; fully-qualified-class
// [ type                   type[]
// ( arg-types ) ret-type   method type


/**
 * Created by neville on 19/01/2017.
 */
public class MemoryAnalyser {

    private static String filename;

    private Set<DynamicFact> dynamicFacts = new ConcurrentHashSet<>();

    public MemoryAnalyser(String filename) {

        this.filename = "/home/neville/Downloads/jetty-distribution-9.4.0.v20161208/java.hprof";
        this.filename = filename;
    }

    public void resolveFactsFromDump() throws IOException, InterruptedException {
        Snapshot snapshot = DumpParsingUtil.getSnapshotFromFile(filename);

        System.out.println("Extracting facts from heap dump...");

        Set<DynamicInstanceFieldPointsTo> dynamicInstanceFieldPointsToSet = new ConcurrentHashSet<>();
        Set<DynamicArrayIndexPointsTo> dynamicArrayIndexPointsToSet = new ConcurrentHashSet<>();
        Set<DynamicStaticFieldPointsTo> dynamicStaticFieldPointsToSet = new ConcurrentHashSet<>();

        Enumeration<JavaHeapObject> instances = snapshot.getThings();
        Collections.list(instances).parallelStream().forEach(heap -> {
            if (heap instanceof JavaObject) {
                JavaObject obj = (JavaObject) heap;
                String baseHeap = getAllocationAbstraction(obj);
                JavaClass clazz = obj.getClazz();
                do {
                    for (JavaField field : clazz.getFields()) {
                        JavaThing fieldValue = obj.getField(field.getName());
                        dynamicInstanceFieldPointsToSet.add(new DynamicInstanceFieldPointsTo(baseHeap, field.getName(), clazz.getName(), getAllocationAbstraction(fieldValue)));
                    }
                } while ((clazz = clazz.getSuperclass()) != null);
            } else if (heap instanceof  JavaObjectArray) {
                JavaObjectArray obj = (JavaObjectArray) heap;
                String baseHeap = getAllocationAbstraction(obj);
                for (JavaThing value : obj.getElements()) {
                    if (value != null)
                        dynamicArrayIndexPointsToSet.add(new DynamicArrayIndexPointsTo(baseHeap, getAllocationAbstraction(value)));
                }
            } else if (heap instanceof  JavaValueArray) {
                // TODO
            } else if (heap instanceof JavaClass) {
                JavaClass obj = (JavaClass) heap;
                for (JavaStatic javaStatic : obj.getStatics()) {
                    dynamicStaticFieldPointsToSet.add(new DynamicStaticFieldPointsTo(
                            javaStatic.getField().getName(), obj.getName(),
                            getAllocationAbstraction(javaStatic.getValue())
                    ));
                }
            } else {
                throw new RuntimeException("Unknown: " + heap.getClass().toString());
            }
        });


        dynamicFacts.addAll(dynamicStaticFieldPointsToSet);
        dynamicFacts.addAll(dynamicInstanceFieldPointsToSet);
        dynamicFacts.addAll(dynamicArrayIndexPointsToSet);

    }

    public int getAndOutputFactsToDB(File factDir) throws IOException, InterruptedException {
        Database db = new Database(factDir);

        try {
            resolveFactsFromDump();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        int unmatched = 0;
        for (DynamicFact fact: dynamicFacts) {
            fact.write_fact(db);
            if (fact.isProbablyUnmatched()) unmatched++;
        }

        System.out.println("Warning, "+unmatched+" heap dump objects are likely not to hav a static counterpart.");
        db.flush();
        db.close();
        return dynamicFacts.size();
    }

    private String getAllocationAbstraction(JavaThing heap) {
        if (heap instanceof JavaObject) {
            JavaObject obj = (JavaObject) heap;
            StackTrace trace = obj.getAllocatedFrom();
            DynamicHeapAllocation heapAbstraction = null;
            /*if(obj.getClazz().isString()) {
                JavaThing value = obj.getField("value");
                if (value instanceof JavaValueArray) {
                    String stringValue = ((JavaValueArray) value).valueString();
                    if (stringValue.length() < 128) {
                        heapAbstraction = new DynamicStringHeapAllocation(stringValue);
                    }
                }
            }
            // else
            if (heapAbstraction == null)*/
                heapAbstraction = DumpParsingUtil.getHeapRepresentation(trace, obj.getClazz());

            dynamicFacts.addAll(DynamicReachableMethod.fromStackTrace(trace));

            dynamicFacts.addAll(DynamicCallGraphEdge.fromStackTrace(trace));

            dynamicFacts.add(heapAbstraction);
            return heapAbstraction.getRepresentation();

        } else if (heap instanceof  JavaObjectArray || heap instanceof  JavaValueArray) {
            JavaHeapObject obj = (JavaHeapObject) heap;
            DynamicHeapAllocation heapRepresentation = DumpParsingUtil.getHeapRepresentation(obj.getAllocatedFrom(), obj.getClazz());

            dynamicFacts.add(heapRepresentation);
            return heapRepresentation.getRepresentation();
        } else if (heap instanceof JavaValue) {
            return "Primitive Object";

        } else if (heap instanceof JavaObjectRef) {
            JavaObjectRef obj = (JavaObjectRef) heap;
            return "JavaObjectRef";
        } else if (heap instanceof JavaClass) {
            return "TODO";
        }
        throw new RuntimeException("Unknown: " + heap.getClass().toString());

    }


}
