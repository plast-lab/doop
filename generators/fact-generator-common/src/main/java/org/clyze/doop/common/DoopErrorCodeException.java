package org.clyze.doop.common;

// An exception that terminates execution with an error code. Used
// instead of System.exit() to allow use of Doop as a library.
public class DoopErrorCodeException extends Exception {

    private final int errorCode;
    @SuppressWarnings("WeakerAccess")
    public boolean fatal = false;
    public static final String PREFIX = "Doop error #";

    public static DoopErrorCodeException error0() {
        return new DoopErrorCodeException(0);
    }

    public static DoopErrorCodeException error1() {
        return new DoopErrorCodeException(1, null, true);
    }

    public static DoopErrorCodeException error2() {
        return new DoopErrorCodeException(2, null, true);
    }

    public static DoopErrorCodeException error3() {
        return new DoopErrorCodeException(3, null, true);
    }

    public static DoopErrorCodeException error4(String msg) {
        return new DoopErrorCodeException(4, msg);
    }

    public static DoopErrorCodeException error5(String msg) {
        return new DoopErrorCodeException(5, msg);
    }

    public static DoopErrorCodeException error6(String msg) {
        return new DoopErrorCodeException(6, msg);
    }

    public static DoopErrorCodeException error7() {
        return new DoopErrorCodeException(7, null, true);
    }

    public static DoopErrorCodeException error8(Throwable t) {
        return new DoopErrorCodeException(8, t);
    }

    public static DoopErrorCodeException error9() {
        return new DoopErrorCodeException(9);
    }

    public static DoopErrorCodeException error10() {
        return new DoopErrorCodeException(10);
    }

    public static DoopErrorCodeException error11(Throwable t) {
        return new DoopErrorCodeException(11, t);
    }

    public static DoopErrorCodeException error12(Throwable t) {
        return new DoopErrorCodeException(12, t);
    }

    public static DoopErrorCodeException error13(String msg) {
        return new DoopErrorCodeException(13, msg);
    }

    public static DoopErrorCodeException error14(String msg) {
        return new DoopErrorCodeException(14, msg);
    }

    public static DoopErrorCodeException error15() {
        return new DoopErrorCodeException(15);
    }

    public static DoopErrorCodeException error16() {
        return new DoopErrorCodeException(16);
    }

    public static DoopErrorCodeException error17(Throwable t) {
        return new DoopErrorCodeException(17, t);
    }

    public static DoopErrorCodeException error18(Throwable t) {
        return new DoopErrorCodeException(18, t, true);
    }

    public static DoopErrorCodeException error19() {
        return new DoopErrorCodeException(19);
    }

    public static DoopErrorCodeException error20(String msg) {
        return new DoopErrorCodeException(20, msg);
    }

    public static DoopErrorCodeException error21() {
        return new DoopErrorCodeException(21, null, true);
    }

    public static DoopErrorCodeException error22() {
        return new DoopErrorCodeException(22);
    }

    public static DoopErrorCodeException error23() {
        return new DoopErrorCodeException(23);
    }

    public static DoopErrorCodeException error24(String msg) {
        return new DoopErrorCodeException(24, msg);
    }

    public static DoopErrorCodeException error25(Throwable t) {
        return new DoopErrorCodeException(25, t);
    }

    public static DoopErrorCodeException error26(String msg) {
        return new DoopErrorCodeException(26, msg);
    }

    public static DoopErrorCodeException error27(String msg) {
        return new DoopErrorCodeException(27, msg);
    }

    public static DoopErrorCodeException error28(String msg) {
        return new DoopErrorCodeException(28, msg);
    }

    public static DoopErrorCodeException error29(String msg) {
        return new DoopErrorCodeException(29, msg);
    }

    public static DoopErrorCodeException error30(String msg) {
        return new DoopErrorCodeException(30, msg);
    }

    public static DoopErrorCodeException error31(String msg) {
        return new DoopErrorCodeException(31, msg);
    }

    public static DoopErrorCodeException error32(String msg) {
        return new DoopErrorCodeException(32, msg);
    }

    public static DoopErrorCodeException error33(String msg) {
        return new DoopErrorCodeException(33, msg);
    }

    public static DoopErrorCodeException error34(String msg) {
        return new DoopErrorCodeException(34, msg);
    }

    public static DoopErrorCodeException error35(String msg) {
        return new DoopErrorCodeException(35, msg);
    }

    private DoopErrorCodeException(int errorCode, Throwable original) {
        super(PREFIX + errorCode, original);
        this.errorCode = errorCode;
    }

    private DoopErrorCodeException(int errorCode, String msg) {
        this(errorCode, new RuntimeException(msg));
    }

    private DoopErrorCodeException(int errorCode, Throwable original, boolean fatal) {
        this(errorCode, original);
        this.fatal = fatal;
    }

    private DoopErrorCodeException(int errorCode) {
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
