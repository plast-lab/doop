package org.clyze.deepdoop.system;

public interface ISourceItem {
	default SourceLocation location() { return null; }
}
