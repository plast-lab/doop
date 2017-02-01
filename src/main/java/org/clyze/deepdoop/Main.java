package org.clyze.deepdoop;

import java.io.IOException;
import org.clyze.analysis.Helper;
import org.clyze.deepdoop.system.Compiler;

public class Main {
	public static void main(String[] args) throws IOException {
		String doopHome = System.getenv("DOOP_HOME");
		Helper.initLogging("INFO", doopHome + "/build/logs", true);

		try {
			System.out.println(Compiler.compile("build", args[0]));
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}
}
