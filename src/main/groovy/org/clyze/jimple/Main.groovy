package org.clyze.jimple

import static groovy.io.FileType.FILES

args.each {
	if (new File(it).isDirectory())
		new File(it).eachFileMatch(FILES, ~/.*jimple/) { f -> Parser.parse(f as String) }
	else
		Parser.parse(it)
}
