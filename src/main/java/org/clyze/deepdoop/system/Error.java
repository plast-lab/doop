package org.clyze.deepdoop.system;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;

public enum Error {
	CMD_CONSTRAINT,
	CMD_DIRECTIVE,
	CMD_EVAL,
	CMD_RULE,
	CMD_NO_DECL,
	CMD_NO_IMPORT,
	DEP_CYCLE,
	DEP_GLOBAL,
	ID_IN_USE,
	NO_DECL,
	DECL_UNKNOWN_VAR;

	static Map<Error, String> _msgMap;
	static {
		_msgMap = new EnumMap<>(Error.class);
		_msgMap.put(Error.CMD_CONSTRAINT, "Constraints are not supported in a command block");
		_msgMap.put(Error.CMD_DIRECTIVE, "Invalid directive in command block `{0}`");
		_msgMap.put(Error.CMD_EVAL, "EVAL property already specified in command block `{0}`");
		_msgMap.put(Error.CMD_RULE, "Normal rules are not supported in a command block");
		_msgMap.put(Error.CMD_NO_DECL, "Predicate `{0}` is imported but has no declaration");
		_msgMap.put(Error.CMD_NO_IMPORT, "Predicate `{0}` is declared but not imported");
		_msgMap.put(Error.DEP_CYCLE, "Cycle detected in the dependency graph of components");
		_msgMap.put(Error.DEP_GLOBAL, "Reintroducing predicate `{0}` to global space");
		_msgMap.put(Error.ID_IN_USE, "Id `{0}` already used to initialize a component");
		_msgMap.put(Error.NO_DECL, "Predicate `{0}` used but not declared");
		_msgMap.put(Error.DECL_UNKNOWN_VAR, "Unknown var `{0}` appears in declaration");
	}

	static String idToMsg(Error errorId, Object[] values) {
		return "ERROR: " + MessageFormat.format(_msgMap.get(errorId), values);
	}
}
