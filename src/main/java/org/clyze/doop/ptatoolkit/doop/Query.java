package org.clyze.doop.ptatoolkit.doop;

/**
 * Queries for accessing Doop database
 */
public enum Query {

    // points-to set
    Stats_Simple_InsensVarPointsTo,

    // field points-to graph
	OBJ,
	OBJ_TYPE,
	IFPT,
	APT,

    // object
	SPECIAL_OBJECTS,
	MERGE_OBJECTS,
	STRING_CONSTANTS,
	REF_STRING_CONSTANTS,

	OBJECT_IN,
	OBJECT_ASSIGN,
    REF_OBJECT,

	STRING_CONSTANT,

	// flow graph
	LOCAL_ASSIGN,
	INTERPROCEDURAL_ASSIGN,
	INSTANCE_LOAD,
	ARRAY_LOAD,
	INSTANCE_STORE,
	ARRAY_STORE,
	INSTANCE_LOAD_FROM_TO,
	ARRAY_LOAD_FROM_TO,
	// currently not considering special calls
	CALL_RETURN_TO,


	// call graph edges
	REGULARCALL,
	REFCALL,
	NATIVECALL,
	CALL_EDGE,
	CALLER_CALLEE,
	MAINMETHOD,
	REACHABLE,
	Reachable,
	IMPLICITREACHABLE,

    // instance field store
    INSTANCE_STORE_IN,
    ARRAY_STORE_IN,

	// call site
	INST_CALL,
	INST_CALL_RECV,
	INST_CALL_ARGS,
	CALLSITEIN,

    // method
	THIS_VAR,
	PARAMS,
	RET_VARS,
	// only instance methods have this variables
	INST_METHODS,
	OBJFINAL,
    VAR_IN,
    METHOD_MODIFIER,

	// type
	APPLICATION_CLASS,
	DIRECT_SUPER_TYPE,
	DECLARING_CLASS_ALLOCATION,

	Method_Neighbor,
	Method_TotalVPT,
	Method_NumberOfNeighbors,
	Method_NumberOfContexts
}
