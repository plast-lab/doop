package org.clyze.jimple

import static groovy.io.FileType.DIRECTORIES
import static groovy.io.FileType.FILES

args.each { handle it }

def handle(String file) {
	if (new File(file).isDirectory()) {
		new File(file).eachFileMatch(DIRECTORIES, ~/.*/) { handle it as String }
		new File(file).eachFileMatch(FILES, ~/.*(jimple|shimple)/) {
			println JimpleListenerImpl.parseJimple2JSON(it as String, ".")
		}
	}
	else
		println JimpleListenerImpl.parseJimple2JSON(file, ".")
}
