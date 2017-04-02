package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.*;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.stream.Stream;

import static org.clyze.doop.dynamicanalysis.DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame;

/**
 * Created by neville on 15/03/2017.
 */
public class HeapAbstractionIndexer2ObjH extends HeapAbstractionIndexer {

    public static final String HCTX_RECORDER_CLASS_NAME = "Instrumentation.Recorder.Recorder$ObjectAndContext";
    public static final String EDGE_CTXS_RECORDER_CLASS_NAME = "Instrumentation.Recorder.Recorder$EdgeContexts";


    public HeapAbstractionIndexer2ObjH(Snapshot snapshot) {
        super(snapshot);
        indexContext2ObjH(snapshot);

    }

    private void indexContext2ObjH(Snapshot snapshot) {

        System.out.println("Indexing heap dump (1H heap ctx)...");
        getAllocationAbstractionTuple(
                HCTX_RECORDER_CLASS_NAME,
                "WARNING: heap context info not found",
                "hctx", "obj").forEach(a -> {
                Context hctxFact =  getHContextFromDumpObject(a[0]);
                addFact(hctxFact);
                DynamicHeapObject objFact = getHeapRepresentation(a[1], hctxFact);
                addFact(objFact);
                heapIndex.put(a[0].getId(), objFact);

        });

        System.out.println("Indexed "+ heapIndex.size() + " heap objects with context.");

        System.out.println("Acquiring call graph edge contexts (2H calling ctx)...");

        getAllocationAbstractionTuple(
                EDGE_CTXS_RECORDER_CLASS_NAME,
                "WARNING: calling ctx info not found",
                "ctxFrom", "ctxTo").forEach(a -> {
                    // get edge
                    StackFrame[] frames = a[0].getAllocatedFrom().getFrames();
                    StackFrame fromFrame = frames[3];
                    StackFrame toFrame = frames[2];
                    Context ctxFrom = getContextFromDumpObject(a[1]);
                    Context ctxTo = getContextFromDumpObject(a[2]);
                    addFact(ctxFrom);
                    addFact(ctxTo);
                    addFact(new DynamicCallGraphEdge(
                            DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame(fromFrame),
                            fromFrame.getLineNumber(),
                            DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame(toFrame),
                            ctxFrom.getRepresentation(), ctxTo.getRepresentation())
                    );

        });


    }

    private Stream<JavaHeapObject[]> getAllocationAbstractionTuple(String tupleClassName, String warningMessage, String... fieldNames) {
        JavaClass clazz = snapshot.findClass(tupleClassName);
        if (clazz == null) {
            System.err.println(warningMessage);
            return Stream.empty();
        }
        Enumeration<?> tupleInstances = clazz.getInstances(false);
        if (tupleInstances == null) {
            System.err.println(warningMessage);
            return Stream.empty();
        }
        return Collections.list(tupleInstances).parallelStream().map(a -> {
            JavaHeapObject[] res = new JavaHeapObject[fieldNames.length + 1];
            JavaObject tupleObject = (JavaObject) a;
            res[0] = tupleObject;
            for (int i = 0; i< fieldNames.length; i++) {
                try{
                    res[i+1] = (JavaHeapObject) tupleObject.getField(fieldNames[i]);
                } catch (ClassCastException e) {
                    return null;
                }
            }
            return res;

        }).filter(Objects::nonNull);

    }

    private Context getHContextFromDumpObject(JavaHeapObject hctx) {
        JavaClass cls = hctx.getClazz();

        StackFrame frame = getAllocationFrame(hctx);

        if (frame == null) return ContextInsensitive.get();

        String fullyQualifiedMethodSignature = fullyQualifiedMethodSignatureFromFrame(frame);

        return new ContextObj(frame.getLineNumber(), fullyQualifiedMethodSignature, cls.getName(), true);

    }

    private Context getContextFromDumpObject(JavaHeapObject ctx) {
        DynamicHeapObject heapAbstraction = heapIndex.getOrDefault(ctx.getId(), null);
        if (heapAbstraction == null) {
            return getHContextFromDumpObject(ctx);
        } else {
            try {
                return new DoubleContext<>(new ContextObj(heapAbstraction.getHeapRepresentation()),
                        new ContextObj(heapAbstraction.getContextRepresentation()));

            } catch (ClassCastException e) {
                return ContextInsensitive.get();
            }

        }



    }

}
