package org.clyze.deepdoop.system;

public class DeepDoopException extends RuntimeException {

	public final ErrorId errorId;

	public DeepDoopException(String msg, ErrorId errorId) {
		super(msg);
		this.errorId = errorId;
	}

	@Override
	public Throwable fillInStackTrace() {
		return null;
	}
}
