package org.clyze.deepdoop.system

class Result {

	enum Kind { LOGIC, IMPORT, EXPORT, CMD }

	Kind kind
	File file
	String cmd

	Result(Kind kind, File file) {
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
