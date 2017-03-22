package org.clyze.deepdoop.system

import java.text.MessageFormat

enum ErrorId {
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
	UNKNOWN_PRED,
	UNKNOWN_VAR,
	UNUSED_VAR,
	UNKNOWN_COMP,
	MULTIPLE_ENT_DECLS

	static Map<ErrorId, String> _msgMap
	static {
		_msgMap = new EnumMap<>(ErrorId.class)
		_msgMap.put(ErrorId.CMD_CONSTRAINT, "Constraints are not supported in a command block")
		_msgMap.put(ErrorId.CMD_DIRECTIVE, "Invalid directive in command block `{0}`")
		_msgMap.put(ErrorId.CMD_EVAL, "EVAL property already specified in command block `{0}`")
		_msgMap.put(ErrorId.CMD_RULE, "Normal rules are not supported in a command block")
		_msgMap.put(ErrorId.CMD_NO_DECL, "Predicate `{0}` is imported but has no declaration")
		_msgMap.put(ErrorId.CMD_NO_IMPORT, "Predicate `{0}` is declared but not imported")
		_msgMap.put(ErrorId.DEP_CYCLE, "Cycle detected in the dependency graph of components")
		_msgMap.put(ErrorId.DEP_GLOBAL, "Reintroducing predicate `{0}` to global space")
		_msgMap.put(ErrorId.ID_IN_USE, "Id `{0}` already used to initialize a component")
		_msgMap.put(ErrorId.NO_DECL, "Predicate `{0}` used but not declared")
		_msgMap.put(ErrorId.UNKNOWN_PRED, "Unknown predicate `{0}` used in propagation")
		_msgMap.put(ErrorId.UNKNOWN_VAR, "Unknown var `{0}`")
		_msgMap.put(ErrorId.UNUSED_VAR, "Unused var `{0}`")
		_msgMap.put(ErrorId.UNKNOWN_COMP, "Unknown component `{0}`")
		_msgMap.put(ErrorId.MULTIPLE_ENT_DECLS, "Multiple declarations for Entity `{0}` in previous components")
	}

	static String idToMsg(ErrorId errorId, Object[] values) {
		MessageFormat.format(_msgMap.get(errorId), values)
	}
}