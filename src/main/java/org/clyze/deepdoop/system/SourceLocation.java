package org.clyze.deepdoop.system;

import java.util.stream.Stream;
import java.util.StringJoiner;

// A "stack" of source lines (due to #include)
// The first element of the array is at the top of the stack, etc.
public class SourceLocation {

	// (SourceFile, LineNumber) pair
	public static class Line {
		String file;
		int    num;
		public Line(String file, int num) {
			this.file = file;
			this.num = num;
		}
	}

	public Line[] lines;

	public SourceLocation(Line[] lines) {
		this.lines = lines;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner("\n");
		Stream.of(lines).forEach(line -> joiner.add("\tat " + line.file + ":" + line.num));
		return joiner.toString();
	}
}
