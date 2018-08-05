package org.clyze.jimple

import static groovy.io.FileType.FILES

if (!args) {
    System.out.println("Usage: jimple2json jimple-directory");
}

args.each { dir ->
	def f = new File(dir)
	if (f.isDirectory())
		f.eachFileRecurse(FILES) { JimpleListenerImpl.parseJimple(it as String, dir, {}) }
}
