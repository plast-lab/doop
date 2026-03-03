package org.clyze.doop.ptatoolkit;

/**
 * Global runtime configuration flags shared by toolkit components.
 */
public class Global {

    private static boolean debug = false;

    /**
     * Enables or disables debug mode.
     *
     * @param debug the new debug flag value
     */
    public static void setDebug(boolean debug) {
        Global.debug = debug;
    }

    /**
     * Returns whether debug mode is enabled.
     *
     * @return {@code true} if debug mode is enabled
     */
    public static boolean isDebug() {
        return debug;
    }

    public static final int UNDEFINE = -1;

    // Zipper
    private static String flow = null;

    /**
     * Returns the selected flow mode.
     *
     * @return the configured flow mode
     */
    public static String getFlow() {
        return flow;
    }

    /**
     * Sets the selected flow mode.
     *
     * @param flow the flow mode
     */
    public static void setFlow(String flow) {
        Global.flow = flow;
    }

    private static boolean enableWrappedFlow = true;

    /**
     * Returns whether wrapped flow is enabled.
     *
     * @return {@code true} when wrapped flow is enabled
     */
    public static boolean isEnableWrappedFlow() {
        return enableWrappedFlow;
    }

    static void setEnableWrappedFlow(boolean enableWrappedFlow) {
        Global.enableWrappedFlow = enableWrappedFlow;
    }

    private static boolean enableUnwrappedFlow = true;

    /**
     * Returns whether unwrapped flow is enabled.
     *
     * @return {@code true} when unwrapped flow is enabled
     */
    public static boolean isEnableUnwrappedFlow() {
        return enableUnwrappedFlow;
    }

    static void setEnableUnwrappedFlow(boolean enableUnwrappedFlow) {
        Global.enableUnwrappedFlow = enableUnwrappedFlow;
    }

    private static boolean isExpress = false;

    /**
     * Returns whether express mode is enabled.
     *
     * @return {@code true} when express mode is enabled
     */
    public static boolean isExpress() {
        return isExpress;
    }

    static void setExpress(boolean isExpress) {
        Global.isExpress = isExpress;
    }


    private static int thread = UNDEFINE;

    /**
     * Returns the configured worker thread count.
     *
     * @return the thread count, or {@link #UNDEFINE}
     */
    public static int getThread() {
        return thread;
    }

    static void setThread(int thread) {
        Global.thread = thread;
    }

    // Scaler
    private static int tst = UNDEFINE;

    /**
     * Returns the total scalability threshold.
     *
     * @return the threshold value, or {@link #UNDEFINE}
     */
    public static int getTST() {
        return tst;
    }

    static void setTST(int tst) {
        Global.tst = tst;
    }

    private static boolean listContext = false;

    /**
     * Returns whether context listing is enabled.
     *
     * @return {@code true} if contexts should be listed
     */
    public static boolean isListContext() {
        return listContext;
    }

    static void setListContext(boolean listContext) {
        Global.listContext = listContext;
    }
}
