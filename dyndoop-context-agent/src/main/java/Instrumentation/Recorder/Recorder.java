package Instrumentation.Recorder;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by neville on 14/03/2017.
 */
public final class Recorder {
    private static final int INITIAL_CAPACITY = 0x10000;
    private static final ArrayList<ObjectAndContext> objects = new ArrayList<>(INITIAL_CAPACITY);
    public static void record(Object hctx, Object obj) {
        objects.add(new ObjectAndContext(hctx, obj));
    }

    private static ConcurrentHashMap<Thread, Object> thisPerThread = new ConcurrentHashMap<>();

    public static Object getThis() {
        return thisPerThread.get(Thread.currentThread());
    }

    public static void setThis(Object that) {
        thisPerThread.put(Thread.currentThread(), that);
    }

    private static final class ObjectAndContext {
        private final Object hctx, obj;

        private ObjectAndContext(Object hctx, Object obj) {
            this.hctx = hctx;
            this.obj = obj;
        }
    }
}
