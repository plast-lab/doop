package org.clyze.jimple

import static groovy.io.FileType.FILES

args.each {
	if (new File(it).isDirectory())
		new File(it).eachFileMatch(FILES, ~/.*(jimple|shimple)/) { f -> println Parser.parseJimple2JSON(f as String, '.') }
	else
		println Parser.parseJimple2JSON(it, '.')
}
