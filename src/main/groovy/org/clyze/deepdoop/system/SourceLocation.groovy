package org.clyze.deepdoop.system

import groovy.transform.Canonical

// A "stack" of source lines (due to #include)
// The first element of the array is at the top of the stack, etc.
@Canonical
class SourceLocation {

	@Canonical
	static class SourceLine {
		String file
		int num
	}

	SourceLine[] lines

	String toString() { lines.collect{ "\tat ${it.file} : ${it.num}" }.join("\n") }
}