package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private final int errorCode;

    public DoopErrorCodeException(int errorCode, Throwable original) {
        super("Doop error " + errorCode, original);
        this.errorCode = errorCode;
    }

    public DoopErrorCodeException(int errorCode) {
        this(errorCode, null);
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
