package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
public class HeapAbstractionIndexer2ObjH extends HeapAbstractionIndexer {

    public static final String HCTX_RECORDER_CLASS_NAME = "Instrumentation.Recorder$ObjectAndContext";

    public HeapAbstractionIndexer2ObjH(Snapshot snapshot) {
        super(snapshot);
        indexContext2ObjH(snapshot);

    }

    private void indexContext2ObjH(Snapshot snapshot) {

        System.out.println("Indexing heap dump (2objH)...");
        JavaClass clazz = snapshot.findClass(HCTX_RECORDER_CLASS_NAME);
        if (clazz == null)
            return;
        Enumeration objectAndContextInstances = clazz.getInstances(false);
        if (objectAndContextInstances == null)
            return;
        Collections.list(objectAndContextInstances).parallelStream().forEach(a -> {
            JavaObject objectAndContext = (JavaObject) a;
            JavaHeapObject hctx = (JavaHeapObject) objectAndContext.getField("hctx");
            JavaHeapObject obj = (JavaHeapObject) objectAndContext.getField("obj");
            Context hctxFact =  getHContextFromDumpObject(hctx);
            addFact(hctxFact);
            DynamicHeapObject objFact = getHeapRepresentation(obj, hctxFact);
            heapIndex.put(obj, objFact);
        });

    }

    private Context getHContextFromDumpObject(JavaHeapObject hctx) {
        JavaClass cls = hctx.getClazz();

        StackFrame frame = DumpParsingUtil.getAllocationFrame(hctx);

        if (frame == null) return new ContextInsensitive();

        String fullyQualifiedMethodName = fullyQualifiedMethodSignatureFromFrame(frame);

        return new ContextObj(frame.getLineNumber(), fullyQualifiedMethodName, cls.getName());

    }

}
