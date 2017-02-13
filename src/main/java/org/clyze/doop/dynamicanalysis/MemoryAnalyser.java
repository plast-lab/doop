package org.clyze.doop.dynamicanalysis;

import com.google.common.collect.*;
import com.sun.jdi.*;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.tools.hat.internal.model.*;
import org.clyze.doop.common.Database;

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

    private Set<DynamicHeapAllocation> heapAllocations = new HashSet<>();

    public MemoryAnalyser(String filename) {

        this.filename = "/home/neville/Downloads/jetty-distribution-9.4.0.v20161208/java.hprof";
        this.filename = filename;
    }

    public Set<DynamicFact> getFactsFromDump() throws IOException, InterruptedException {
        //String heapDumpFile = doHeapDump();
        Snapshot snapshot = DumpParsingUtil.getSnapshotFromFile(filename);
        Set<DynamicInstanceFieldPointsTo> dynamicInstanceFieldPointsToSet = new HashSet<>();
        Set<DynamicArrayIndexPointsTo> dynamicArrayIndexPointsToSet = new HashSet<>();
        Set<DynamicStaticFieldPointsTo> dynamicStaticFieldPointsToSet = new HashSet<>();

        Enumeration<JavaHeapObject> instances = snapshot.getThings();

        while(instances.hasMoreElements()) {
            JavaHeapObject heap = instances.nextElement();

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
        }
        return Sets.union(Sets.union(dynamicStaticFieldPointsToSet, dynamicInstanceFieldPointsToSet), dynamicArrayIndexPointsToSet);

    }

    public int getAndOutputFactsToDB(File factDir) throws IOException, InterruptedException {
        Database db = new Database(factDir);
        final Set<DynamicFact> factsFromDump = getFactsFromDump();
        for (DynamicFact fact: factsFromDump) {
            fact.write_fact(db);
        }
        for (DynamicHeapAllocation heapAllocation: heapAllocations) {
            heapAllocation.write_fact(db);
        }
        db.flush();
        db.close();
        return factsFromDump.size();
    }

    private String getFieldSignature(JavaField field, JavaClass obj) {
        return obj.getName() +
                ": " +
                DumpParsingUtil.convertType(field.getSignature())[0] + " " + field.getName();
    }

    private String getAllocationAbstraction(JavaThing heap) {
        if (heap instanceof JavaObject) {
            JavaObject obj = (JavaObject) heap;
            StackTrace trace = obj.getAllocatedFrom();

            DynamicHeapAllocation heapAbstraction = DumpParsingUtil.getHeapRepresentation(trace, obj.getClazz());

            heapAllocations.add(heapAbstraction);
            return heapAbstraction.getRepresentation();
        } else if (heap instanceof  JavaObjectArray) {
            JavaObjectArray obj = (JavaObjectArray) heap;
            DynamicHeapAllocation heapRepresentation = DumpParsingUtil.getHeapRepresentation(obj.getAllocatedFrom(), obj.getClazz());

            heapAllocations.add(heapRepresentation);
            return heapRepresentation.getRepresentation();
        } else if (heap instanceof JavaValue) {
            return "Primitive Object";

        } else if (heap instanceof JavaObjectRef) {
            JavaObjectRef obj = (JavaObjectRef) heap;
            return "JavaObjectRef";
        } else if (heap instanceof  JavaValueArray) {
            JavaValueArray obj = (JavaValueArray) heap;
            return "Array of: " + obj.getClazz().getName();
        } else if (heap instanceof JavaClass) {
            return "TODO";
        }
        throw new RuntimeException("Unknown: " + heap.getClass().toString());

    }

    private static ArrayList<Map<LocalVariable,Value>> doMonitorDebug(VirtualMachine virtualMachine) {
        EventRequestManager erm = virtualMachine.eventRequestManager();
        MethodEntryRequest methodEntryRequest = erm.createMethodEntryRequest();
        methodEntryRequest.addClassExclusionFilter("java.");
        methodEntryRequest.addClassExclusionFilter("javax.");
        methodEntryRequest.enable();
        EventQueue eventQueue = virtualMachine.eventQueue();
        ArrayList<Map<LocalVariable,Value>> variableMaps = new ArrayList<>();
        while (true) {
            EventSet eventSet = null;
            try {
                eventSet = eventQueue.remove();
            } catch (InterruptedException e) {
                return variableMaps;
            }
            for (Event event : eventSet) {
                if (event instanceof VMDeathEvent
                        || event instanceof VMDisconnectEvent) {
                    // exit
                    return variableMaps;
                } else if (event instanceof IntegerType) {
                    MethodExitEvent meEvent = (MethodExitEvent) event;
                    // TODO
                    try {
                        ImmutableSetMultimap.Builder<LocalVariable, Value> builder = ImmutableSetMultimap.builder();
                        for (StackFrame frame : meEvent.thread().frames()) {
                            try {
                                frame.getValues(frame.visibleVariables()).entrySet().forEach(builder::put);
                            } catch (AbsentInformationException e) {

                            }
                        }

                    } catch (IncompatibleThreadStateException e) {
                        e.printStackTrace();
                    }
                } else if (event instanceof MethodEntryEvent) {
                    MethodEntryEvent meEvent = (MethodEntryEvent) event;
                    try {
                        StackFrame frame = meEvent.thread().frame(0);
                        variableMaps.add(frame.getValues(frame.visibleVariables()));
                    } catch (IncompatibleThreadStateException e) {
                    } catch (AbsentInformationException e) {

                    }


                } else if (event instanceof ModificationWatchpointEvent) {
                    // a Test.foo has changed
                    ModificationWatchpointEvent modEvent = (ModificationWatchpointEvent) event;
                    System.out.println("old="
                            + modEvent.valueCurrent());
                    System.out.println("new=" + modEvent.valueToBe());
                    System.out.println();
                }
            }
            eventSet.resume();
        }
    }

}
