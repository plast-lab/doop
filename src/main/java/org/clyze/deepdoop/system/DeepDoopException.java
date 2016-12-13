package org.clyze.deepdoop.system;

public class DeepDoopException extends RuntimeException {

	public final Error errorId;

	public DeepDoopException(String msg, Error errorId, Object... values) {
		super("\n" + msg);
		this.errorId = errorId;
	}

	@Override
	public Throwable fillInStackTrace() {
		return null;
	}
}
