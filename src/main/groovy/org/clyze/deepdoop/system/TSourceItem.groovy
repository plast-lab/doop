package org.clyze.deepdoop.system

trait TSourceItem {
	SourceLocation loc = SourceManager.instance.getLastLoc()
}
