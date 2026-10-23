package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test the P/Taint source vocabulary (souffle-logic/addons/information-flow):
 * TaintSourceReturn, TaintSourceFormalParam, and the default override expansion.
 *
 * Drives a context-insensitive analysis of the tests/117-taint-sources driver
 * with --information-flow minimal and the spec + property queries in
 * addons/testing/TestTaintSources.dl attached. Each query derives a FAIL
 * relation that must be EMPTY when the feature works.
 */
class TestTaintSources extends DoopSpec {

    @Unroll
    def "Test 117 (taint sources: return, formal parameter, override expansion)"() {
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
    }
}
