package org.clyze.deepdoop.system;

import java.util.Stack;

public class SourceManager {

	// A C-Preprocessor line marker
	static class LineMarker {
		public int    markerLine;       // the line that the last marker reports
		public int    markerActualLine; // the line that the last marker is in the output file
		public String sourceFile;       // the source file tha the last marker reports

		public LineMarker(int markerLine, int markerActualLine, String sourceFile) {
			this.markerLine = markerLine;
			this.markerActualLine = markerActualLine;
			this.sourceFile = sourceFile;
		}
	}

	Stack<LineMarker>            _markers;

	// Singleton
	private static SourceManager _instance;
	private SourceManager() {
		_markers = new Stack<>();
	}
	public static SourceManager v() {
		if (_instance == null)
			_instance = new SourceManager();
		return _instance;
	}

	public void lineMarkerStart(int markerLine, int markerActualLine, String sourceFile) {
		_markers.push(new LineMarker(markerLine, markerActualLine, sourceFile));
	}
	public void lineMarkerEnd() {
		_markers.pop();
	}

	public SourceLocation getLoc(int outputLine) {
		SourceLocation.Line[] lines = new SourceLocation.Line[_markers.size()];
		int actualLine = outputLine;
		// Iterate in reverse order, because the top of the stack is at the "end"
		for (int i = _markers.size()-1 ; i >= 0 ; --i) {
			LineMarker lm = _markers.get(i);
			int sourceLine = (lm.markerLine + actualLine - (lm.markerActualLine+1));
			lines[i] = new SourceLocation.Line(lm.sourceFile, sourceLine);
			actualLine = lm.markerActualLine;
		}
		return new SourceLocation(lines);
	}
}
