package Instrumentation.Recorder;



import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by neville on 14/03/2017.
 */
public final class Recorder {
    private static final int INITIAL_CAPACITY = 0x10000;
    private static final ArrayList<ObjectAndContext> objectAndContexts = new ArrayList<>(INITIAL_CAPACITY);
    private static final ArrayList<EdgeContexts> edgeContexts = new ArrayList<>(INITIAL_CAPACITY);

    private static ConcurrentHashMap<FrameId, Object> thisMap = new ConcurrentHashMap<>();

    // BEWARE! Dragons! Do not modify this code!

    public static void recordCall(Object receiver) {
        Object previousReceiver = thisMap.get(FrameId.getCurrent());
        if (previousReceiver != null)
            edgeContexts.add(new EdgeContexts(previousReceiver, receiver));
    }

    public static void mergeStatic() {
        Object receiver = thisMap.get(FrameId.getCurrent());
        if (receiver == null) {
            thisMap.remove(FrameId.getNext());
        } else {
            thisMap.put(FrameId.getNext(), receiver);
        }
    }

    public static void merge(Object receiver) {
        thisMap.put(FrameId.getCurrent(), receiver);
    }

    public static void recordStatic(Object obj) {
        Object hctx = thisMap.get(FrameId.getCurrent());
        //if (hctx == null) return;
        objectAndContexts.add(new ObjectAndContext(hctx, obj));
    }

    public static void record(Object hctx, Object obj) {
        //if (hctx == null) return;
        objectAndContexts.add(new ObjectAndContext(hctx, obj));
    }


    private static final class ObjectAndContext {
        //TODO use soft references here
        private final Object hctx, obj;

        private ObjectAndContext(Object hctx, Object obj) {
            this.hctx = hctx;
            this.obj = obj;
        }
    }

    private static final class EdgeContexts {
        //TODO use soft references here
        private final Object ctxFrom, ctxTo;

        private EdgeContexts(Object ctxFrom, Object ctxTo) {
            this.ctxFrom = ctxFrom;
            this.ctxTo = ctxTo;
        }
    }

    private static final class FrameId {
        private final long threadId;
        private final int depth;

        static FrameId getCurrent() {
            Thread thread = Thread.currentThread();
            return new FrameId(thread.getId(), thread.getStackTrace().length - 2);
        }

        static FrameId getNext() {
            Thread thread = Thread.currentThread();
            return new FrameId(thread.getId(), thread.getStackTrace().length - 1);
        }

        private FrameId(long threadId, int depth) {
            this.threadId = threadId;
            this.depth = depth;
        }

        @Override
        public boolean equals(Object o) {
            FrameId that = (FrameId) o;

            if (threadId != that.threadId) return false;
            return depth == that.depth;
        }

        @Override
        public int hashCode() {
            int result = (int) (threadId ^ (threadId >>> 32));
            result = 31 * result + depth;
            return result;
        }


    }
}
