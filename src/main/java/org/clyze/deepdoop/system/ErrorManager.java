package org.clyze.deepdoop.system;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ErrorManager {

	public static void warn(ErrorId errorId, Object... values) {
		String msg = "[DD] WARNING: " + ErrorId.idToMsg(errorId, values);
		LogFactory.getLog(ErrorManager.class).warn(msg);
	}

	public static void error(ErrorId errorId, Object... values) {
		error(null, errorId, values);
	}
	public static void error(SourceLocation loc, ErrorId errorId, Object... values) {
		String msg = "[DD] ERROR: " + ErrorId.idToMsg(errorId, values);
		if (loc != null) msg = msg + "\n" + loc;
		throw new DeepDoopException(msg, errorId);
	}
}

