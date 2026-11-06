package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test the P/Taint source vocabulary (souffle-logic/addons/information-flow):
 * TaintSourceReturn, TaintSourceFormalParam, TaintSourceActualParam, TaintSourceBase,
 * TaintSourceThis, TaintSourceField, TaintSourceType (mock and mark halves), and
 * the default override expansion; and, in a second run with
 * --enable-model-primitives, the arithmetic taint mark (the result of an
 * application binop/unop with a tainted operand) and a primitive formal source.
 *
 * Drives a context-insensitive analysis of the tests/117-taint-sources driver
 * with --information-flow minimal and the spec + property queries in
 * addons/testing/TestTaintSources.dl attached. Each query derives a FAIL
 * relation that must be EMPTY when the feature works.
 */
class TestTaintSources extends DoopSpec {

    @Unroll
    def "Test 117 (taint sources: return, formal/actual parameter, base, this, field, type, override expansion)"() {
        when:
        List options = ["--platform", "java_17",
                        "--information-flow", "minimal",
                        "--extra-logic",
                        "${Doop.souffleLogicPath}/addons/testing/TestTaintSources.dl"] + souffleInterpreter
        Analysis analysis = analyzeBuiltinTest("117-taint-sources", options,
                                               "context-insensitive", "test-117-taint-sources")

        then:
        sourceChecks(analysis)
        // Negative: arithmetic over a trusted buffer never leaks (primitives are
        // not modeled here, so int sinks fed by arithmetic see no value at all).
        relationIsEmpty(analysis, "Taint_FAIL_ArithNegLeaks")
    }

    @Unroll
    def "Test 117 with --enable-model-primitives (arithmetic taint mark)"() {
        when:
        List options = ["--platform", "java_17",
                        "--information-flow", "minimal",
                        "--enable-model-primitives",
                        "--extra-logic",
                        "${Doop.souffleLogicPath}/addons/testing/TestTaintSources.dl"] + souffleInterpreter
        Analysis analysis = analyzeBuiltinTest("117-taint-sources", options,
                                               "context-insensitive", "test-117-taint-sources-prim")

        then:
        // Every source check holds unchanged with primitives modeled.
        sourceChecks(analysis)
        // Arithmetic mark: a length decoded from the bytes of the tainted buffer
        // reaches sinkInt, attributed to the buffer's source.
        relationIsEmpty(analysis, "Taint_FAIL_ArithTaintMissing")
        // Negative: the same arithmetic over the trusted buffer never leaks (each
        // binop has its own <prim-arith> object).
        relationIsEmpty(analysis, "Taint_FAIL_ArithNegLeaks")
        // Scope: no <prim-arith> object of a library arithmetic site is tainted.
        relationIsEmpty(analysis, "Taint_FAIL_LibraryArithMarked")
        // A primitive formal-parameter source works through the mark on the
        // caller's primitive object.
        relationIsEmpty(analysis, "Taint_FAIL_PrimFormalMarkMissing")
    }

    // The source-vocabulary checks shared by both runs (each call asserts).
    private static void sourceChecks(Analysis analysis) {
        // The spec names only methods that exist (guards typos in the spec itself).
        relationIsEmpty(analysis, "Taint_FAIL_SpecUnmatched")
        // Return source: every sink fed by untrusted() leaks under its label.
        relationIsEmpty(analysis, "Taint_FAIL_ReturnSourceMissing")
        // Override expansion is the default: a source on Reader.read fires for
        // the call resolved to FileReader.read.
        relationIsEmpty(analysis, "Taint_FAIL_OverrideExpansionMissing")
        // Formal-parameter source: handle's formal is tainted on entry.
        relationIsEmpty(analysis, "Taint_FAIL_FormalParamSourceMissing")
        // Negatives: a non-source feed never leaks, including taint at a
        // non-sink argument index.
        relationIsEmpty(analysis, "Taint_FAIL_NonSourceLeaks")
        // Labels: only the spec's labels appear in findings.
        relationIsEmpty(analysis, "Taint_FAIL_UnexpectedLabel")
        // A primitive-typed (int) return source reaches a primitive sink.
        relationIsEmpty(analysis, "Taint_FAIL_PrimSourceMissing")
        // Actual-parameter source: read(buf) taints buf at the call site.
        relationIsEmpty(analysis, "Taint_FAIL_ActualParamSourceMissing")
        // ...and an element loaded from the filled buffer is tainted too.
        relationIsEmpty(analysis, "Taint_FAIL_FilledElementMissing")
        // Base source: p.load() taints p; new Conn(..) taints the constructed object.
        relationIsEmpty(analysis, "Taint_FAIL_BaseSourceMissing")
        // This source: `this` of validate() is tainted on entry.
        relationIsEmpty(analysis, "Taint_FAIL_ThisSourceMissing")
        // Negatives: the *Neg sinks, fed only by non-source values, never leak.
        relationIsEmpty(analysis, "Taint_FAIL_CallerSideNegLeaks")
        // Field source, mock half: a load of a source field leaks, with or
        // without a store into the field.
        relationIsEmpty(analysis, "Taint_FAIL_FieldSourceMissing")
        // Field source, mark half: the real object held by the field is tainted
        // itself, so an alias that never goes through the field leaks too.
        relationIsEmpty(analysis, "Taint_FAIL_FieldMarkMissing")
        // Negative: a non-source field never leaks.
        relationIsEmpty(analysis, "Taint_FAIL_FieldNegLeaks")
        // Type source, mark half: every object of the type or a subtype is
        // tainted, wherever it was allocated.
        relationIsEmpty(analysis, "Taint_FAIL_TypeSourceMissing")
        // Type source, mock half: a never-allocated type still reaches sinks from
        // its entry positions (a formal, a field, a body-less method's result).
        relationIsEmpty(analysis, "Taint_FAIL_TypeMockMissing")
        // Mark half of the method-keyed sources: the real application object at
        // a return / formal / actual / base / this position is tainted itself,
        // so a holder that never went through the position leaks too.
        relationIsEmpty(analysis, "Taint_FAIL_MethodKeyedMarkMissing")
    }
}
