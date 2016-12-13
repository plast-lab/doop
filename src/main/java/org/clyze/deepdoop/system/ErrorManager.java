package org.clyze.deepdoop.system;

import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class ErrorManager {

	Stack<LineMarker> _markers;
	ParserRuleContext _lastCtx;

	// Singleton
	private static ErrorManager _instance;
	private ErrorManager() {
		_markers = new Stack<>();
	}
	public static ErrorManager v() {
		if (_instance == null)
			_instance = new ErrorManager();
		return _instance;
	}

	public void lineMarkerStart(int markerLine, int markerActualLine, String sourceFile) {
		_markers.push(new LineMarker(markerLine, markerActualLine, sourceFile));
	}
	public void lineMarkerEnd() {
		_markers.pop();
	}

	public void newContext(ParserRuleContext ctx) {
		_lastCtx = ctx;
	}

	public void error(Error errorId, Object... values) {
		int line = _lastCtx.start.getLine();
		StringBuilder sb = new StringBuilder("[DD] " + Error.idToMsg(errorId, values));
		while (!_markers.empty()) {
			LineMarker lm = _markers.pop();
			sb.append("\n\tat " + lm.sourceFile + ":" + (lm.markerLine + line - (lm.markerActualLine+1)));
			line = lm.markerActualLine;
		}
		throw new DeepDoopException(sb.toString(), errorId, values);
	}
}

class LineMarker {
	public int    markerLine;       // the line that the last marker reports
	public int    markerActualLine; // the line that the last marker is in the output file
	public String sourceFile;       // the source file tha the last marker reports

	public LineMarker(int markerLine, int markerActualLine, String sourceFile) {
		this.markerLine = markerLine;
		this.markerActualLine = markerActualLine;
		this.sourceFile = sourceFile;
	}
}
