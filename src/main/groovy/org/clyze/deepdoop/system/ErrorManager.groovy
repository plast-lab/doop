package org.clyze.deepdoop.system

import org.apache.commons.logging.LogFactory

class ErrorManager {

	static void warn(ErrorId errorId, Object... values) {
		def msg = "[DD] WARNING: " + ErrorId.idToMsg(errorId, values)
		LogFactory.getLog(ErrorManager.class).warn(msg)
	}

	static void error(ErrorId errorId, Object... values) {
		error(null, errorId, values)
	}
	static void error(SourceLocation loc, ErrorId errorId, Object... values) {
		def msg = "[DD] ERROR: " + ErrorId.idToMsg(errorId, values)
		if (loc != null) msg = msg + "\n" + loc
		throw new DeepDoopException(msg, errorId)
	}
}

