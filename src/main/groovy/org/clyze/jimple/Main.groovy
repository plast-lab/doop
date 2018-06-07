package org.clyze.jimple

import static groovy.io.FileType.FILES

args.each { dir ->
	def f = new File(dir)
	if (f.isDirectory())
		f.eachFileRecurse(FILES) { println JimpleListenerImpl.parseJimple(it as String, dir, {}) }
}
