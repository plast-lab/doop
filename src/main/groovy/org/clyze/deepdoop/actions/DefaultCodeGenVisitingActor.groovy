package org.clyze.deepdoop.actions

import org.clyze.deepdoop.system.Result

class DefaultCodeGenVisitingActor extends PostOrderVisitor<String> implements IActor<String>, TDummyActor<String> {

	File outDir

	AtomCollectingActor acActor     = new AtomCollectingActor()
	Map<IVisitable, String> codeMap = [:]
	List<Result> results            = []

	DefaultCodeGenVisitingActor(File outDir) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
		this.outDir = outDir
	}
}
