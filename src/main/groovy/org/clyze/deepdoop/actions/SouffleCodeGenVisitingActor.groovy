package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.Program

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	String visit(Program p) {
		// Transform program before visiting nodes
		def flatP = p.accept(new PostOrderVisitor<IVisitable>(new NormalizingActor(p.comps))) as Program
		return super.visit(flatP)
	}
}
