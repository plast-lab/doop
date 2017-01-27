package org.clyze.jimple

import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

public class Parser {

	public static void parse(String filename) {
		JimpleParser parser = new JimpleParser(
				new CommonTokenStream(
					new JimpleLexer(
						new ANTLRFileStream(filename))))
		JimpleListenerImpl listener = new JimpleListenerImpl(filename)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())


		def origFile = new File(filename)
		def dir = origFile.getAbsoluteFile().getParentFile()
		// abc.def.Foo
		def simplename = FilenameUtils.removeExtension( FilenameUtils.getName(filename) )
		def i = simplename.lastIndexOf(".")
		// abc.def
		def packages = simplename[0..i]
		// Foo
		def classname = simplename[(i+1)..-1]
		// abc.def.Foo.json
		new File(dir, simplename + ".json").withWriter { it << listener.json }
		// abc/def
		def path = new File(dir, packages.replaceAll("\\.", "/"))

		path.mkdirs()
		FileUtils.copyFile(origFile, new File(path, classname + ".jimple"))
	}
}
