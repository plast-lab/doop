package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
class HeapAbstractionIndexer {
    private static final String UNKNOWN = "Unknown";
    final Map<Long, DynamicHeapObject> heapIndex = new ConcurrentHashMap<>();
    final Map<AllocationKey, DynamicHeapObject> frameIndex = new ConcurrentHashMap<>();
    final Snapshot snapshot;

    private final Set<DynamicFact> dynamicFacts = new ConcurrentHashSet<>();

    HeapAbstractionIndexer(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    protected StackFrame getAllocationFrame(JavaHeapObject obj) {

        StackTrace trace = obj.getAllocatedFrom();
        // Store dynamic edges
        dynamicFacts.addAll(DynamicCallGraphEdge.fromStackTrace(trace));
        dynamicFacts.addAll(DynamicReachableMethod.fromStackTrace(trace));

        JavaClass clazz = obj.getClazz();
        if (trace != null && trace.getFrames().length > 0) {
            StackFrame[] frames = trace.getFrames();

            int relevantIndex = 0;
            // find index where object is allocated
            if (clazz.isArray() || clazz.getName().equals("java.lang.Object") || !frames[0].getMethodName().equals("<init>")) {
                relevantIndex = 0;
            } else {
                for (int i = 0; i < frames.length - 1; i++) {
                    if (frames[i].getClassName().equals(clazz.getName()) && frames[i].getMethodName().equals("<init>"))
                        relevantIndex = i + 1;
                    else if (relevantIndex > 0) break;
                }
            }

            return frames[relevantIndex];
        }
        return null;

    }

    DynamicHeapObject getHeapRepresentation(JavaHeapObject obj, Context hctx) {
        JavaClass cls = obj.getClazz();

        if (cls.isString() && obj.toString().length() < 50) {
            // Strings are treated differently throughout, important for reflection analysis
            JavaThing value = ((JavaObject)obj).getField("value");
            String strValue = value instanceof JavaValueArray?((JavaValueArray)value).valueString():UNKNOWN;
            return new DynamicStringHeapObject(strValue);
        }

        StackFrame frame = getAllocationFrame(obj);

        Function<AllocationKey, DynamicHeapObject> heapObjectFromFrame = k -> {
            StackFrame f = k.getFrame();

            String fullyQualifiedMethodName = fullyQualifiedMethodSignatureFromFrame(f);

            return new DynamicNormalHeapObject(f.getLineNumber(), fullyQualifiedMethodName, cls.getName(), hctx.getRepresentation());
        };

        final AllocationKey allocationKey = new AllocationKey(frame, cls);
        // This is the point to perform caching
        
        if (hctx.equals(ContextInsensitive.get()) && frame != null) {
            return frameIndex.computeIfAbsent(allocationKey, heapObjectFromFrame) ;
        }


        if (frame == null) return new DynamicNormalHeapObject(UNKNOWN, UNKNOWN, cls.getName(), hctx.getRepresentation());

        return heapObjectFromFrame.apply(allocationKey);
    }

    Set<DynamicFact> getDynamicFacts() {
        return dynamicFacts;
    }

    // public facade with caching
    String getAllocationAbstraction(JavaThing obj) {
        if (obj instanceof JavaValueArray ||
                obj instanceof JavaObjectArray ||
                obj instanceof JavaObject) {
            JavaHeapObject heapObject = (JavaHeapObject) obj;
            DynamicHeapObject heapAbstraction = heapIndex.getOrDefault(heapObject.getId(), null);
            if (heapAbstraction != null) return heapAbstraction.getRepresentation();

            heapAbstraction = getHeapRepresentation(heapObject, ContextInsensitive.get());

            addFact(heapAbstraction);

            return heapAbstraction.getRepresentation();

        } else if (obj instanceof JavaValue) {
            return "Primitive Object";
        } else if (obj instanceof JavaObjectRef) {
            return "JavaObjectRef";
        } else if (obj instanceof JavaClass) {
            return "<class " + ((JavaClass) obj).getName() + ">";
        }
        throw new RuntimeException("Unknown: " + obj.getClass().toString());
    }


    void addFact(DynamicFact fact) {
        dynamicFacts.add(fact);
    }
}
