package org.clyze.deepdoop.system;

public class ErrorManager {

	public static void error(Error errorId, Object... values) {
		error(null, errorId, values);
	}
	public static void error(SourceLocation loc, Error errorId, Object... values) {
		String msg = "[DD] " + Error.idToMsg(errorId, values);
		if (loc != null) msg = msg + "\n" + loc;
		throw new DeepDoopException(msg, errorId);
	}
}

