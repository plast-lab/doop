package org.clyze.jimple

import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.io.FilenameUtils

public class Parser {

	public static void parse(String filename) {
		JimpleParser parser = new JimpleParser(
				new CommonTokenStream(
					new JimpleLexer(
						new ANTLRFileStream(filename))))
		JimpleListenerImpl listener = new JimpleListenerImpl(filename)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		def dir = new File(filename).getAbsoluteFile().getParentFile()
		def simplename = FilenameUtils.getName(filename)
		def jsonFile = new File(dir, FilenameUtils.removeExtension(simplename) + ".json")

		jsonFile << listener.json
	}
}
