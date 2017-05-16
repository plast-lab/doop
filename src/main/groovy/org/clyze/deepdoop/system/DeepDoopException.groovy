package org.clyze.deepdoop.system

class DeepDoopException extends RuntimeException {

	ErrorId errorId

	DeepDoopException(String msg, ErrorId errorId) {
		super(msg)
		this.errorId = errorId
	}

	synchronized Throwable fillInStackTrace() { null }
}