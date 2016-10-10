package org.clyze.deepdoop.system;

import java.nio.file.Path;

public class Result {

	public enum Kind { LOGIC, IMPORT, EXPORT, CMD }

	public final Kind kind;
	public final Path file;
	public final String cmd;

	public Result(Kind kind, Path file) {
		this.kind = kind;
		this.file = file;
		this.cmd  = null;
	}

	public Result(String cmd) {
		this.kind = Kind.CMD;
		this.file = null;
		this.cmd  = cmd;
	}

	@Override
	public String toString() {
		return "(" + kind + ") " + (file != null ? file.toString() : cmd);
	}
}
