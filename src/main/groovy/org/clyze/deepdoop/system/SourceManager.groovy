package org.clyze.deepdoop.system

class SourceManager {

	// A C-Preprocessor line marker
	static class LineMarker {
		public int    markerLine       // the line that the last marker reports
		public int    markerActualLine // the line that the last marker is in the output file
		public String sourceFile       // the source file tha the last marker reports

		LineMarker(int markerLine, int markerActualLine, String sourceFile) {
			this.markerLine = markerLine
			this.markerActualLine = markerActualLine
			this.sourceFile = sourceFile
		}
	}

	Stack<LineMarker>     _markers
	Stack<SourceLocation> _locations
	String                _outputFile

	// Singleton
	private static SourceManager _instance
	private SourceManager() {
		_markers   = []
		_locations = []
	}
	static SourceManager v() {
		if (_instance == null)
			_instance = new SourceManager()
		return _instance
	}

	void setOutputFile(String outputFile) {
		_outputFile = outputFile
	}

	void lineMarkerStart(int markerLine, int markerActualLine, String sourceFile) {
		_markers.push(new LineMarker(markerLine, markerActualLine, sourceFile))
	}
	void lineMarkerEnd() {
		_markers.pop()
	}

	void recLoc(int outputLine) {
		_locations.push(location(outputLine))
	}
	SourceLocation getLastLoc() {
		return _locations.empty() ? null : _locations.pop()
	}

	SourceLocation location(int outputLine) {
		SourceLine[] lines
		if (_markers.empty()) {
			lines = [ new SourceLine(_outputFile, outputLine) ]
		}
		else {
			lines = new SourceLine[_markers.size()]
			def actualLine = outputLine
			// Iterate in reverse order, because the top of the stack is at the "end"
			for (int i = _markers.size()-1 ; i >= 0 ; --i) {
				def lm = _markers.get(i)
				def sourceLine = (lm.markerLine + actualLine - (lm.markerActualLine+1))
				lines[i] = new SourceLine(lm.sourceFile, sourceLine)
				actualLine = lm.markerActualLine
			}
		}
		return new SourceLocation(lines)
	}
}
