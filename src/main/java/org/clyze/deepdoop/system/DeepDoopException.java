package org.clyze.deepdoop.system;

public class DeepDoopException extends RuntimeException {

	public final Error errorId;

	public DeepDoopException(Error errorId, Object... values) {
		super("[DD] " + Error.idToMsg(errorId, values));
		this.errorId = errorId;
	}

	@Override
	public Throwable fillInStackTrace() {
		return null;
	}
}
