package org.clyze.deepdoop.system

// A "stack" of source lines (due to #include)
// The first element of the array is at the top of the stack, etc.
class SourceLocation {

	public SourceLine[] lines

	SourceLocation(SourceLine[] lines) {
		this.lines = lines
	}

	String toString() {
		return lines.collect{ "\tat ${it.file} : ${it.num}" }.join('\n')
	}
}
