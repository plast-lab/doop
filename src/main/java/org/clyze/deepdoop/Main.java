package org.clyze.deepdoop;

import org.clyze.deepdoop.system.Compiler;

public class Main {
	public static void main(String[] args) {
		System.out.println(Compiler.compile("build", args[0]));
	}
}
