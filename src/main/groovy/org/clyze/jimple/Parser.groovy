package org.clyze.jimple

import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

import org.clyze.persistent.doop.BasicMetadata

class Parser {

	static String parse(String filename) {
		File sourceFile = new File(filename) //does the sourceFileName gets reported in the same way with the jcplugin?

		JimpleParser parser = new JimpleParser(
				new CommonTokenStream(
					new JimpleLexer(
						new ANTLRFileStream(sourceFile as String))))
		JimpleListenerImpl listener = new JimpleListenerImpl(sourceFile as String)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		// XYZ/abc.def.Foo.json
		//new File(dir, simplename + ".json").withWriter { it << listener.json }
		return listener.metadata.variables.collect{ it.doopId }.join(", ")
	}

    static BasicMetadata parseJimple(String filename) {
		/*
		Are these needed?

		// XYZ/abc.def.Foo.jimple
		def origFile = new File(filename)
		// XYZ
		def dir = origFile.getParentFile()
		dir = dir ?: new File(".")
		// abc.def.Foo
		def simplename = FilenameUtils.removeExtension( FilenameUtils.getName(filename) )
		def i = simplename.lastIndexOf(".")
		// abc.def
		def packages = simplename[0..i]
		// Foo
		def classname = simplename[(i+1)..-1]
		File sourceFile
		if (i != -1) {
			// XYZ/abc/def
			def path = new File(dir, packages.replaceAll("\\.", "/"))
			path.mkdirs()
			// XYZ/abc/def/Foo.jimple
			sourceFile = new File(path, classname + ".jimple")
			FileUtils.copyFile(origFile, sourceFile)
		}
		else
			sourceFile = origFile
		*/

		File sourceFile = new File(filename) //does the sourceFileName gets reported in the same way with the jcplugin?

		JimpleParser parser = new JimpleParser(
				new CommonTokenStream(
					new JimpleLexer(
						new ANTLRFileStream(sourceFile as String))))
		JimpleListenerImpl listener = new JimpleListenerImpl(sourceFile as String)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		return listener.metadata
	}
}
