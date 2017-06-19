package org.clyze.deepdoop

import org.clyze.deepdoop.system.Compiler

//println Compiler.compileToLB(args[0], new File("build"))
println Compiler.compileToSouffle(args[0], new File("build"))
