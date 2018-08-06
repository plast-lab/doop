package org.clyze.jimple

import static groovy.io.FileType.FILES

if (!args)
	println "Usage: jimple2json -Pargs=<jimple-directory>"

args.each { dir ->
	def f = new File(dir)
	if (f.isDirectory())
		f.eachFileRecurse(FILES) { JimpleListenerImpl.parseJimple(it as String, dir, {}) }
}
