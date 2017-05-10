package org.clyze.deepdoop.system

trait TSourceItem {
	SourceLocation loc = SourceManager.v().getLastLoc()
}
