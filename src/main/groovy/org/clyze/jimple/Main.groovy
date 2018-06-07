package org.clyze.jimple

import static groovy.io.FileType.DIRECTORIES
import static groovy.io.FileType.FILES

args.each { handle it, it }

def handle(String file, String baseDir) {
	if (new File(file).isDirectory()) {
		new File(file).eachFileMatch(DIRECTORIES, ~/.*/) { handle it as String, baseDir }
		new File(file).eachFileMatch(FILES, ~/.*(jimple|shimple)/) {
			println JimpleListenerImpl.parseJimple2JSON(it as String, "build/jimple/", baseDir)
		}
	}
}
