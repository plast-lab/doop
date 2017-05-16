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
	NO_DECL_REC,
	UNKNOWN_PRED,
	UNKNOWN_VAR,
	UNUSED_VAR,
	UNKNOWN_COMP,
	MULTIPLE_ENT_DECLS

	static Map<ErrorId, String> msgMap
	static {
		msgMap = new EnumMap<>(ErrorId.class)
		msgMap[CMD_CONSTRAINT] = "Constraints are not supported in a command block"
		msgMap[CMD_DIRECTIVE] = "Invalid directive in command block `{0}`"
		msgMap[CMD_EVAL] = "EVAL property already specified in command block `{0}`"
		msgMap[CMD_RULE] = "Normal rules are not supported in a command block"
		msgMap[CMD_NO_DECL] = "Predicate `{0}` is imported but has no declaration"
		msgMap[CMD_NO_IMPORT] = "Predicate `{0}` is declared but not imported"
		msgMap[DEP_CYCLE] = "Cycle detected in the dependency graph of components"
		msgMap[DEP_GLOBAL] = "Reintroducing predicate `{0}` to global space"
		msgMap[ID_IN_USE] = "Id `{0}` already used to initialize a component"
		msgMap[NO_DECL] = "Predicate `{0}` used but not declared"
		msgMap[NO_DECL_REC] = "Predicate `{0}` used with @past but not declared"
		msgMap[UNKNOWN_PRED] = "Unknown predicate `{0}` used in propagation"
		msgMap[UNKNOWN_VAR] = "Unknown var `{0}`"
		msgMap[UNUSED_VAR] = "Unused var `{0}`"
		msgMap[UNKNOWN_COMP] = "Unknown component `{0}`"
		msgMap[MULTIPLE_ENT_DECLS] = "Multiple declarations for Entity `{0}` in previous components"
	}

	static String idToMsg(ErrorId errorId, Object[] values) {
		MessageFormat.format(msgMap.get(errorId), values)
	}
}
