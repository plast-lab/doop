package org.clyze.deepdoop.system;

public class ErrorManager {

	// Singleton
	private static ErrorManager _instance;
	private ErrorManager() {}
	public static ErrorManager v() {
		if (_instance == null)
			_instance = new ErrorManager();
		return _instance;
	}


	int    _lastMarkerLine;       // the line that the last marker reports
	int    _lastMarkerActualLine; // the line that the last marker is in the output file
	// TODO support a stack of files
	String _lastSourceFile;       // the source file tha the last marker reports

	public void newLineMarker(int markerLine, int markerActualLine, String sourceFile) {
		_lastMarkerLine = markerLine;
		_lastMarkerActualLine = markerActualLine;
		_lastSourceFile = sourceFile;
	}

	public void error(Error errorId, Object... values) {
		throw new DeepDoopException(errorId, values);
	}
}
