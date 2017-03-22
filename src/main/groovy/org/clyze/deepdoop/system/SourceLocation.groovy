package org.clyze.deepdoop.system

// A "stack" of source lines (due to #include)
// The first element of the array is at the top of the stack, etc.
class SourceLocation {

	static class SourceLine {
		String file
		int    num
	}

	SourceLine[] lines

	String toString() {
		lines.collect{ "\tat ${it.file} : ${it.num}" }.join('\n')
	}
}