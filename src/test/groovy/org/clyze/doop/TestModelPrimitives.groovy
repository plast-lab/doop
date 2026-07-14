package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test the opt-in primitive-value modeling (souffle-logic/main/model-primitives.dl),
 * enabled here via --enable-model-primitives.
 *
 * Drives a context-insensitive analysis of the tests/116-model-primitives driver
 * with the property queries in addons/testing/TestModelPrimitives.dl attached.
 * Each query derives a FAIL relation that must be EMPTY when the feature works,
 * so every assertion below is a relationIsEmpty check.
 */
class TestModelPrimitives extends DoopSpec {

    @Unroll
    def "Test 116 (primitive-value modeling, opt-in via --enable-model-primitives)"() {
        when:
        List options = ["--platform", "java_17",
                        "--enable-model-primitives",
                        "--extra-logic",
                        "${Doop.souffleLogicPath}/addons/testing/TestModelPrimitives.dl"] + souffleInterpreter
        Analysis analysis = analyzeBuiltinTest("116-model-primitives", options,
                                               "context-insensitive", "test-116-model-primitives")

        then:
        // Rule (1): primitive formal parameters are seeded and wrapper-typed.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ParamUnseeded")
        // Rule (2): arithmetic results are seeded.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ArithMissing")
        // Rule (3): numeric constants are seeded (closure-wide) and by the driver itself.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ConstMissing")
        relationIsEmpty(analysis, "ModelPrim_FAIL_DriverConstMissing")
        // Invariant: every <prim-*> object is typed as a boxed wrapper.
        relationIsEmpty(analysis, "ModelPrim_FAIL_BadlyTyped")
        // Interprocedural return flow reaches the caller.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ReturnFlowMissing")
        // Heap flow: primitive-derived objects reach the static and instance fields.
        relationIsEmpty(analysis, "ModelPrim_FAIL_StaticFieldUnseeded")
        relationIsEmpty(analysis, "ModelPrim_FAIL_InstanceFieldUnseeded")
        // Non-int coverage: Integer / Long / Double / Boolean all appear.
        relationIsEmpty(analysis, "ModelPrim_FAIL_TypeMissing")
    }
}
