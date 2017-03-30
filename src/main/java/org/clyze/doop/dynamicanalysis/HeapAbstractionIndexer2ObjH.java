package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;

import java.util.Collections;
import java.util.Enumeration;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
public class HeapAbstractionIndexer2ObjH extends HeapAbstractionIndexer {

    public static final String HCTX_RECORDER_CLASS_NAME = "Instrumentation.Recorder.Recorder$ObjectAndContext";

    public HeapAbstractionIndexer2ObjH(Snapshot snapshot) {
        super(snapshot);
        indexContext2ObjH(snapshot);

    }

    private void warnNoContext() {
        System.err.println("Warning: No context information found!");
    }

    private void indexContext2ObjH(Snapshot snapshot) {

        System.out.println("Indexing heap dump (2objH)...");
        JavaClass clazz = snapshot.findClass(HCTX_RECORDER_CLASS_NAME);
        if (clazz == null) {
            warnNoContext();
            return;
        }
        Enumeration<?> objectAndContextInstances = clazz.getInstances(false);
        if (objectAndContextInstances == null) {
            warnNoContext();
            return;
        }
        Collections.list(objectAndContextInstances).parallelStream().forEach(a -> {
            JavaObject objectAndContext = (JavaObject) a;
            JavaHeapObject hctx = null;
            JavaHeapObject obj = null;
            try {
                hctx = (JavaHeapObject) objectAndContext.getField("hctx");
                obj = (JavaHeapObject) objectAndContext.getField("obj");
            } catch (ClassCastException e) {
                System.err.println("Unknown heap object: " + objectAndContext.getField("obj"));
                return;
            }
            Context hctxFact =  getHContextFromDumpObject(hctx);
            addFact(hctxFact);
            DynamicHeapObject objFact = getHeapRepresentation(obj, hctxFact);
            addFact(objFact);
            heapIndex.put(obj.getId(), objFact);
        });

        System.out.println("Indexed "+ heapIndex.size() + " heap objects with context.");


    }

    private Context getHContextFromDumpObject(JavaHeapObject hctx) {
        JavaClass cls = hctx.getClazz();

        StackFrame frame = getAllocationFrame(hctx);

        if (frame == null) return ContextInsensitive.get();

        String fullyQualifiedMethodSignature = fullyQualifiedMethodSignatureFromFrame(frame);

        return new ContextObj(frame.getLineNumber(), fullyQualifiedMethodSignature, cls.getName());

    }

}
