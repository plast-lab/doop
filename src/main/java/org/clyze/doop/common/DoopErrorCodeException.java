package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private final int errCode;

    public DoopErrorCodeException(int errCode, Throwable original) {
        super("Doop exception with error code " + errCode, original);
        this.errCode = errCode;
    }

    public DoopErrorCodeException(int errCode) {
        this(errCode, null);
    }

    public int getErrorCode() {
        return errCode;
    }
}
