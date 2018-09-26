package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private final int errorCode;

    public DoopErrorCodeException(int errorCode, Throwable original) {
        super("Doop exception with error code " + errorCode, original);
        this.errorCode = errorCode;
    }

    public DoopErrorCodeException(int errorCode) {
        this(errorCode, null);
    }

    public int getErrorCode() {
        return errorCode;
    }
}
