package org.clyze.jimple

import com.google.gson.GsonBuilder
import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

import org.clyze.persistent.doop.BasicMetadata

class Parser {

	static String parseJimple2JSON(String filename) {
		def metadata = parseJimple(filename)

		def json = [:]
		//json.put("Class", metadata.classes)
		//json.put("Field", metadata.fields)
		//json.put("Method", metadata.methods)
		//json.put("Variable", metadata.variables)
		//json.put("HeapAllocation", metadata.heapAllocations)

		return new GsonBuilder().disableHtmlEscaping().create().toJson(json)
	}

	static BasicMetadata parseJimple(String filename) {
		// XYZ/abc.def.Foo.jimple
		def origFile = new File(filename)
		// XYZ
		def dir = origFile.getParentFile()
		dir = dir ?: new File(".")
		// abc.def.Foo
		def extension = FilenameUtils.getExtension( FilenameUtils.getName(filename) )
		def simplename = FilenameUtils.removeExtension( FilenameUtils.getName(filename) )
		def i = simplename.lastIndexOf(".")
		// abc.def
		def packages = simplename[0..i]
		// Foo
		def classname = simplename[(i+1)..-1]
		File sourceFile
		String sourceFileName
		if (i != -1) {
			packages = packages.replaceAll("\\.", "/")
			// XYZ/abc/def
			def path = new File(dir, packages)
			path.mkdirs()
			// XYZ/abc/def/Foo.jimple
			sourceFile = new File(path, classname + ".$extension")
			FileUtils.copyFile(origFile, sourceFile)
			sourceFileName = packages + classname + ".$extension"
		}
		else {
			//no dot in filename (e.g. no extension? FTB, we need the file to end with .jimple @ server-side)
			sourceFile = origFile
			sourceFileName = origFile.getName()
 		}

		JimpleParser parser = new JimpleParser(
				new CommonTokenStream(
					new JimpleLexer(
						new ANTLRFileStream(sourceFile as String))))
		JimpleListenerImpl listener = new JimpleListenerImpl(sourceFileName)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		return listener.metadata
	}
}
