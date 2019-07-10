package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private final int errorCode;
    @SuppressWarnings("WeakerAccess")
    public boolean fatal = false;
    public static final String PREFIX = "Doop error #";

    public DoopErrorCodeException(int errorCode, Throwable original) {
        super(PREFIX + errorCode, original);
        this.errorCode = errorCode;
    }

    public DoopErrorCodeException(int errorCode, String msg) {
        this(errorCode, new RuntimeException(msg));
    }

    public DoopErrorCodeException(int errorCode, Throwable original, boolean fatal) {
        this(errorCode, original);
        this.fatal = fatal;
    }

    public DoopErrorCodeException(int errorCode) {
        this(errorCode, (Throwable)null);
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        Throwable t = getCause();
        return (t == null) ? super.getMessage() : super.getMessage() + ": " + t.getMessage();
    }
}
