package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import org.clyze.doop.common.Database;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
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

    private HeapAbstractionIndexer heapAbstractionIndexer = null;

    public MemoryAnalyser(String filename) {

        this.filename = filename;
    }



    public void resolveFactsFromDump(String sensitivity) throws IOException, InterruptedException {
        Snapshot snapshot = DumpParsingUtil.getSnapshotFromFile(filename);


        try {
            Class<?> heapAbstractionIndexerClass = Class.forName(
                    "org.clyze.doop.dynamicanalysis.HeapAbstractionIndexer" + sensitivity
            );
            heapAbstractionIndexer = (HeapAbstractionIndexer) heapAbstractionIndexerClass
                    .getConstructor(Snapshot.class)
                    .newInstance(snapshot);
        } catch (InstantiationException | NoSuchMethodException | IllegalAccessException |
                ClassNotFoundException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Extracting facts from heap dump...");

        Set<DynamicInstanceFieldPointsTo> dynamicInstanceFieldPointsToSet = new ConcurrentHashSet<>();
        Set<DynamicArrayIndexPointsTo> dynamicArrayIndexPointsToSet = new ConcurrentHashSet<>();
        Set<DynamicStaticFieldPointsTo> dynamicStaticFieldPointsToSet = new ConcurrentHashSet<>();

        Enumeration<JavaHeapObject> instances = snapshot.getThings();
        Collections.list(instances).parallelStream().forEach(heap -> {
            if (heap instanceof JavaObject) {
                JavaObject obj = (JavaObject) heap;
                String baseHeap = heapAbstractionIndexer.getAllocationAbstraction(obj);
                JavaClass clazz = obj.getClazz();
                do {
                    for (JavaField field : clazz.getFields()) {
                        JavaThing fieldValue = obj.getField(field.getName());
                        dynamicInstanceFieldPointsToSet.add(new DynamicInstanceFieldPointsTo(baseHeap, field.getName(), clazz.getName(), heapAbstractionIndexer.getAllocationAbstraction(fieldValue)));
                    }
                } while ((clazz = clazz.getSuperclass()) != null);
            } else if (heap instanceof  JavaObjectArray) {
                JavaObjectArray obj = (JavaObjectArray) heap;
                String baseHeap = heapAbstractionIndexer.getAllocationAbstraction(obj);
                for (JavaThing value : obj.getElements()) {
                    if (value != null)
                        dynamicArrayIndexPointsToSet.add(new DynamicArrayIndexPointsTo(baseHeap, heapAbstractionIndexer.getAllocationAbstraction(value)));
                }
            } else if (heap instanceof  JavaValueArray) {
                // TODO
            } else if (heap instanceof JavaClass) {
                JavaClass obj = (JavaClass) heap;
                for (JavaStatic javaStatic : obj.getStatics()) {
                    dynamicStaticFieldPointsToSet.add(new DynamicStaticFieldPointsTo(
                            javaStatic.getField().getName(), obj.getName(),
                            heapAbstractionIndexer.getAllocationAbstraction(javaStatic.getValue())
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
            resolveFactsFromDump("2ObjH");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        Context.write_facts_once(db);

        for (DynamicFact fact: dynamicFacts) {
            fact.write_fact(db);
        }

        for (DynamicFact fact: heapAbstractionIndexer.getDynamicFacts()) {
            fact.write_fact(db);
        }

        db.flush();
        db.close();
        return dynamicFacts.size();
    }




}
