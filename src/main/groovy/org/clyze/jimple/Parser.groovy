package org.clyze.jimple

import com.google.gson.GsonBuilder
import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.clyze.persistent.doop.BasicMetadata

class Parser {

	static def getClassInfo(String qualifiedName) {
		def i = qualifiedName.lastIndexOf(".")
		def packageName = i >= 0 ? qualifiedName[0..(i - 1)] : ''
		def className = i >= 0 ? qualifiedName[(i + 1)..-1] : qualifiedName
		[packageName, className]
	}

	static String parseJimple2JSON(String filename, String outPath) {
		def metadata = parseJimple(filename, outPath)

		def json = [:]
		//json.put("Class", metadata.classes)
		//json.put("Field", metadata.fields)
		//json.put("Method", metadata.methods)
		//json.put("Variable", metadata.variables)
		//json.put("MethodInvocation", metadata.invocations)
		//json.put("HeapAllocation", metadata.heapAllocations)
		//json.put("Usage", metadata.usages)

		return new GsonBuilder().disableHtmlEscaping().create().toJson(json)
	}

	static BasicMetadata parseJimple(String filename, String outPath) {
		// XYZ/abc.def.Foo.jimple
		def origFile = new File(filename)
		// XYZ
		def dir
		if (outPath == null) {
			dir = origFile.getParentFile()
			dir = dir ?: new File(".")
		} else {
			dir = outPath
		}
		def extension = FilenameUtils.getExtension(FilenameUtils.getName(filename))
		// abc.def.Foo
		def simplename = FilenameUtils.removeExtension(FilenameUtils.getName(filename))
		def (packageName, className) = getClassInfo(simplename)

		// abc/def
		packageName = packageName.replaceAll("\\.", "/")
		// XYZ/abc/def
		def path = new File(dir, packageName)
		path.mkdirs()
		// XYZ/abc/def/Foo.jimple
		def sourceFile = new File(path, "${className}.$extension")
		if (origFile.canonicalPath != sourceFile.canonicalPath)
			FileUtils.copyFile(origFile, sourceFile)
		// abc/def/Foo.jimple
		def sourceFileName = "${packageName}/${className}.${extension}" as String

		def parser = new JimpleParser(
				new CommonTokenStream(
						new JimpleLexer(
								new ANTLRFileStream(sourceFile as String))))
		def listener = new JimpleListenerImpl(sourceFileName)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		return listener.metadata
	}
}
