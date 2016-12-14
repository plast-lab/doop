package org.clyze.deepdoop.system;

public class ErrorManager {

	public static void error(ErrorId errorId, Object... values) {
		error(null, errorId, values);
	}
	public static void error(SourceLocation loc, ErrorId errorId, Object... values) {
		String msg = "[DD] " + ErrorId.idToMsg(errorId, values);
		if (loc != null) msg = msg + "\n" + loc;
		throw new DeepDoopException(msg, errorId);
	}
}

