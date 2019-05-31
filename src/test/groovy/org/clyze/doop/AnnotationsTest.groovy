package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class AnnotationsTest extends DoopSpec {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 017 (annotations)"() {
		when:
		List options = ["--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
						"--generate-jimple", "--Xserver-logic",
						"--reflection-classic",
						"--thorough-fact-gen", "--sanity"]
		Analysis analysis = analyzeBuiltinTest("017-annotations", options)

		then:
		// type annotations
		varPointsToQ(analysis, '<Main: void testClassAnnotations(java.lang.Class)>/cAnnotations#_28', '<annotations array for type Main at <Main: void testClassAnnotations(java.lang.Class)>/java.lang.Class.getDeclaredAnnotations/0>')
		arrayIndexPointsTo(analysis, '<annotations array for type Main at <Main: void testClassAnnotations(java.lang.Class)>/java.lang.Class.getDeclaredAnnotations/0>', '<annotation TypeAnnotation for Main>', true)
		// method annotations
		varPointsToQ(analysis, '<Main: void testMethodAndParameterAnnotations(java.lang.Class)>/mAnnotations#_48', '<annotations array for method <Main: void annotation(java.lang.String)> at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getDeclaredAnnotations/0>')
		arrayIndexPointsTo(analysis, '<annotations array for method <Main: void annotation(java.lang.String)> at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getDeclaredAnnotations/0>', '<annotation MethodAnnotation for <Main: void annotation(java.lang.String)>>', true)
		// field annotations
		varPointsToQ(analysis, '<Main: void testFieldAnnotations(java.lang.Class)>/fAnnotations#_38', '<annotations array for field <Main: java.lang.String field> at <Main: void testFieldAnnotations(java.lang.Class)>/java.lang.reflect.Field.getDeclaredAnnotations/0>')
		arrayIndexPointsTo(analysis, '<annotations array for field <Main: java.lang.String field> at <Main: void testFieldAnnotations(java.lang.Class)>/java.lang.reflect.Field.getDeclaredAnnotations/0>', '<annotation FieldAnnotation1 for <Main: java.lang.String field>>', true)
		// parameter annotations
		varPointsToQ(analysis, '<Main: void testMethodAndParameterAnnotations(java.lang.Class)>/pAnnotations#6#_53', '<method parameter annotations array for method <Main: void annotation(java.lang.String)> at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getParameterAnnotations/0>')
		arrayIndexPointsTo(analysis, '<method parameter annotations array for method <Main: void annotation(java.lang.String)> at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getParameterAnnotations/0>', '<parameter annotations array for parameter ParameterAnnotation at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getParameterAnnotations/0>', true)
		arrayIndexPointsTo(analysis, '<parameter annotations array for parameter ParameterAnnotation at <Main: void testMethodAndParameterAnnotations(java.lang.Class)>/java.lang.reflect.Method.getParameterAnnotations/0>', '<annotation ParameterAnnotation for parameter 0 of method <Main: void annotation(java.lang.String)>>', true)
		noSanityErrors(analysis)
	}
}
