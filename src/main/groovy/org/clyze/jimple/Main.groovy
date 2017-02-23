package org.clyze.jimple

import static groovy.io.FileType.FILES

args.each {
	if (new File(it).isDirectory())
		new File(it).eachFileMatch(FILES, ~/.*jimple/) { f -> println Parser.parseJimple(f as String) }
	else
		print Parser.parseJimple(it)
}
