package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
class HeapAbstractionIndexer {
    private static final String UNKNOWN = "Unknown";
    final Map<Long, DynamicHeapObject> heapIndex = new ConcurrentHashMap<>();
    final Snapshot snapshot;

    private final Set<DynamicFact> dynamicFacts = new ConcurrentHashSet<>();

    HeapAbstractionIndexer(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    DynamicHeapObject getHeapRepresentation(JavaHeapObject obj, Context hctx) {
        JavaClass cls = obj.getClazz();

        StackFrame frame = DumpParsingUtil.getAllocationFrame(obj);

        if (frame == null) return new DynamicNormalHeapObject(UNKNOWN, UNKNOWN, cls.getName(), hctx.getRepresentation());

        String fullyQualifiedMethodName = fullyQualifiedMethodSignatureFromFrame(frame);


        return new DynamicNormalHeapObject(frame.getLineNumber(), fullyQualifiedMethodName, cls.getName(), hctx.getRepresentation());


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


    void addFact(DynamicFact hctxFact) {
        dynamicFacts.add(hctxFact);
    }
}
