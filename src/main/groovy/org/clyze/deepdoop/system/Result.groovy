package org.clyze.deepdoop.system

import java.nio.file.Path

class Result {

	enum Kind { LOGIC, IMPORT, EXPORT, CMD }

	Kind kind
	Path file
	String cmd

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

	String toString() { "($kind) " + (file ?: cmd) }
}
