package org.clyze.deepdoop.system

import org.clyze.deepdoop.system.SourceLocation.SourceLine

class SourceManager {

	// A C-Preprocessor line marker
	static class LineMarker {
		int    markerLine       // the line that the last marker reports
		int    markerActualLine // the line that the last marker is in the output file
		String sourceFile       // the source file tha the last marker reports

		LineMarker(int markerLine, int markerActualLine, String sourceFile) {
			this.markerLine = markerLine
			this.markerActualLine = markerActualLine
			this.sourceFile = sourceFile
		}
	}

	Stack<LineMarker>     markers
	Stack<SourceLocation> locations
	String                outputFile

	// Singleton
	private static SourceManager instance
	private SourceManager() {
		markers   = []
		locations = []
	}
	static SourceManager v() {
		if (instance == null)
			instance = new SourceManager()
		return instance
	}

	void lineMarkerStart(int markerLine, int markerActualLine, String sourceFile) {
		markers.push(new LineMarker(markerLine, markerActualLine, sourceFile))
	}
	void lineMarkerEnd() {
		markers.pop()
	}

	void recLoc(int outputLine) {
		locations.push(location(outputLine))
	}
	SourceLocation getLastLoc() {
		locations.empty() ? null : locations.pop()
	}

	SourceLocation location(int outputLine) {
		SourceLine[] lines
		if (markers.empty()) {
			lines = [ new SourceLine(file: outputFile, num: outputLine) ]
		}
		else {
			lines = new SourceLine[markers.size()]
			def actualLine = outputLine
			// Iterate in reverse order, because the top of the stack is at the "end"
			for (int i = markers.size()-1 ; i >= 0 ; --i) {
				def lm = markers.get(i)
				def sourceLine = (lm.markerLine + actualLine - (lm.markerActualLine+1))
				lines[i] = new SourceLine(file: lm.sourceFile, num: sourceLine)
				actualLine = lm.markerActualLine
			}
		}
		return new SourceLocation(lines: lines)
	}
}