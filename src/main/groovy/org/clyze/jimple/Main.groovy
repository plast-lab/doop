package org.clyze.jimple

public class Main {
	public static void main(String[] args) {
		args.each { Parser.parse(it) }
	}
}
