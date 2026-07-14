package org.clyze.doop.sarif

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

@CompileStatic @TupleConstructor
class RMetadata {
	String name
	String contentType
	int doopIdPosition
	String resultMessage
	String ruleDescription
	int ruleIndex
	String level
}