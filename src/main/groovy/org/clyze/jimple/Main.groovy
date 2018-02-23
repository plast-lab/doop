package org.clyze.jimple

import static groovy.io.FileType.FILES

args.each {
	if (new File(it).isDirectory())
		new File(it).eachFileMatch(FILES, ~/.*(jimple|shimple)/) { f ->
			println JimpleListenerImpl.parseJimple2JSON(f as String, ".")
		}
	else
		println JimpleListenerImpl.parseJimple2JSON(it, ".")
}
