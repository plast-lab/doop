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
	MULTIPLE_ENT_DECLS,
	INVALID_ANNOTATION,
	UNSUPPORTED_TYPE,
	UNKNOWN_TYPE,
	CONSTRUCTOR_UNKNOWN,
	CONSTRUCTOR_RULE,
	CONSTRUCTOR_INCOMPATIBLE,
	INCOMPATIBLE_TYPES,
	RESERVED_SUFFIX,

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
		msgMap[INVALID_ANNOTATION] = "Invalid annotation `{0}` for `{1}`"
		msgMap[UNSUPPORTED_TYPE] = "Type `{0}` is currently unsupported"
		msgMap[UNKNOWN_TYPE] = "Unknown type `{0}`"
		msgMap[CONSTRUCTOR_UNKNOWN] = "Unknown constructor `{0}`"
		msgMap[CONSTRUCTOR_RULE] = "Constructor `{0}` used as a normal predicate in rule head"
		msgMap[CONSTRUCTOR_INCOMPATIBLE] = "Constructor `{0}` used with incompatible type `{1}`"
		msgMap[INCOMPATIBLE_TYPES] = "Incompatible types for predicate `{0}` (at index {1})"
		msgMap[RESERVED_SUFFIX] = "Suffix `__pArTiAl` is reserved and cannot appear in predicate names"
	}

	static String idToMsg(ErrorId errorId, Object[] values) {
		MessageFormat.format(msgMap.get(errorId), values)
	}
}
