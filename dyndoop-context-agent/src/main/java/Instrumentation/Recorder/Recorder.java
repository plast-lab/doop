package Instrumentation.Recorder;



import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by neville on 14/03/2017.
 */
public final class Recorder {
    private static final int INITIAL_CAPACITY = 0x10000;
    private static final ArrayList<ObjectAndContext> objectAndContexts = new ArrayList<>(INITIAL_CAPACITY);
    private static final ArrayList<EdgeContexts> edgeContexts = new ArrayList<>(INITIAL_CAPACITY);

    private static Object[] previousThis = new Object[0x1000];
    private static Map<Class<?>,Integer> sampled = new ConcurrentHashMap<>();

    // BEWARE! Dragons! Do not modify this code!

    public static void recordCall(Object receiver) {
        Object previousReceiver = previousThis[(int) Thread.currentThread().getId()];
        if (previousReceiver != null) {
            Class<?> klass = receiver.getClass();
            Integer oldO = sampled.get(klass);
            int old = oldO == null ? 0 : oldO.intValue();
            if (old < 1000) {
                sampled.put(klass, new Integer(old + 1));
                edgeContexts.add(new EdgeContexts(previousReceiver, receiver));
            }
        }
    }

    public static void mergeStatic() {
        // do nothing
    }

    public static void merge(Object receiver) {
        previousThis[(int) Thread.currentThread().getId()] = receiver;
    }

    public static void recordStatic(Object obj) {
        Object hctx = previousThis[(int) Thread.currentThread().getId()];
        if (hctx != null) {
            Class<?> klass = hctx.getClass();
            Integer oldO = sampled.get(klass);
            int old = oldO == null ? 0 : oldO.intValue();
            if (old < 1000) {
                sampled.put(klass, new Integer(old + 1));
                objectAndContexts.add(new ObjectAndContext(hctx, obj));
            }
        }
    }

    public static void record(Object hctx, Object obj) {
        if (hctx != null) {
            Class<?> klass = hctx.getClass();
            Integer oldO = sampled.get(klass);
            int old = oldO == null ? 0 : oldO.intValue();
            if (old < 1000) {
                sampled.put(klass, new Integer(old + 1));
                objectAndContexts.add(new ObjectAndContext(hctx, obj));
            }
        }
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


}
