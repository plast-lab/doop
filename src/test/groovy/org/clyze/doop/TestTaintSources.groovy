package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test the P/Taint source vocabulary (souffle-logic/addons/information-flow):
 * TaintSourceReturn, TaintSourceFormalParam, TaintSourceActualParam, TaintSourceBase,
 * TaintSourceThis, and the default override expansion.
 *
 * Drives a context-insensitive analysis of the tests/117-taint-sources driver
 * with --information-flow minimal and the spec + property queries in
 * addons/testing/TestTaintSources.dl attached. Each query derives a FAIL
 * relation that must be EMPTY when the feature works.
 */
class TestTaintSources extends DoopSpec {

    @Unroll
    def "Test 117 (taint sources: return, formal/actual parameter, base, this, override expansion)"() {
        when:
        List options = ["--platform", "java_17",
                        "--information-flow", "minimal",
                        "--extra-logic",
                        "${Doop.souffleLogicPath}/addons/testing/TestTaintSources.dl"] + souffleInterpreter
        Analysis analysis = analyzeBuiltinTest("117-taint-sources", options,
                                               "context-insensitive", "test-117-taint-sources")

        then:
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
    }
}
