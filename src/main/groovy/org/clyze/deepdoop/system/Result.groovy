package org.clyze.deepdoop.system

import java.nio.file.Path

class Result {

	enum Kind { LOGIC, IMPORT, EXPORT, CMD }

	public final Kind kind
	public final Path file
	public final String cmd

	Result(Kind kind, Path file) {
		this.kind = kind
		this.file = file
		this.cmd  = null
	}
	Result(String cmd) {
		this.kind = Kind.CMD
		this.file = null
		this.cmd  = cmd
	}

	String toString() {
		return "($kind) " + (file != null ? file.toString() : cmd)
	}
}
