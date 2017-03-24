package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
public class HeapAbstractionIndexer {
    private static final String UNKNOWN = "Unknown";
    protected final ConcurrentHashMap<JavaThing, DynamicHeapObject> heapIndex = new ConcurrentHashMap<>();
    protected final Snapshot snapshot;

    private final Set<DynamicFact> dynamicFacts = new ConcurrentHashSet<>();

    public HeapAbstractionIndexer(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    protected DynamicHeapObject getHeapRepresentation(JavaHeapObject obj, Context hctx) {
        JavaClass cls = obj.getClazz();

        StackFrame frame = DumpParsingUtil.getAllocationFrame(obj);

        if (frame == null) return new DynamicNormalHeapObject(UNKNOWN, UNKNOWN, cls.getName(), hctx.getRepresentation());

        String fullyQualifiedMethodName = fullyQualifiedMethodSignatureFromFrame(frame);


        return new DynamicNormalHeapObject(frame.getLineNumber(), fullyQualifiedMethodName, cls.getName(), hctx.getRepresentation());


    }

    public Set<DynamicFact> getDynamicFacts() {
        return dynamicFacts;
    }

    // public facade with caching
    public String getAllocationAbstraction(JavaThing obj) {
        if (heapIndex.contains(obj)) {
            return heapIndex.get(obj).getRepresentation();
        }
        if (obj instanceof JavaValueArray ||
                obj instanceof JavaObjectArray ||
                obj instanceof JavaObject) {
            JavaHeapObject heapObject = (JavaHeapObject) obj;
            DynamicHeapObject heapAbstraction = null;

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


    protected void addFact(DynamicFact hctxFact) {
        dynamicFacts.add(hctxFact);
    }
}
