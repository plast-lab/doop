package org.clyze.deepdoop.system

class DeepDoopException extends RuntimeException {

	public final ErrorId errorId

	DeepDoopException(String msg, ErrorId errorId) {
		super(msg)
		this.errorId = errorId
	}

	@Override
	Throwable fillInStackTrace() { return null }
}
