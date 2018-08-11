package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private int errCode;

    public DoopErrorCodeException(int errCode) {
        super("Doop exception with error code " + errCode);
        this.errCode = errCode;
    }

    public int getErrorCode() {
        return errCode;
    }
}
