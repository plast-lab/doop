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
        // Rule (5): primitive conversions are seeded.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ConvMissing")
        // Rule (3b): sub-int (Boolean/Byte/Char/Short) constants are seeded.
        relationIsEmpty(analysis, "ModelPrim_FAIL_SubIntConstMissing")
        // Invariant: every <prim-*> object is typed as a boxed wrapper...
        relationIsEmpty(analysis, "ModelPrim_FAIL_BadlyTyped")
        // ...and is mono-typed (exactly one type — guards the keying).
        relationIsEmpty(analysis, "ModelPrim_FAIL_MultiTyped")
        // Interprocedural return flow reaches the caller.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ReturnFlowMissing")
        // Rule (4): interprocedural argument flow — the caller's primitive
        // object reaches the callee's formal (not merely rule (1)'s self-seed).
        relationIsEmpty(analysis, "ModelPrim_FAIL_ArgFlowMissing")
        // Lock-in: conversion/sub-int actuals (rules 5/3b) flow to the formals
        // of makeLong/makeDouble/invert via rule (4) — closed seeding gap.
        relationIsEmpty(analysis, "ModelPrim_FAIL_FlowFormalUnseeded")
        // Scoping: rule (1) no longer self-seeds internally-called scale.
        relationIsEmpty(analysis, "ModelPrim_FAIL_ScopeSelfSeed")
        // Heap flow: primitive-derived objects reach the static and instance fields.
        relationIsEmpty(analysis, "ModelPrim_FAIL_StaticFieldUnseeded")
        relationIsEmpty(analysis, "ModelPrim_FAIL_InstanceFieldUnseeded")
        // Rule (6): primitive array-element store reaches ArrayIndexPointsTo,
        // and the element is read back by the load (full store->load cycle).
        relationIsEmpty(analysis, "ModelPrim_FAIL_ArrayStoreMissing")
        relationIsEmpty(analysis, "ModelPrim_FAIL_ArrayLoadMissing")
        // Coverage: all 8 wrappers appear (Integer/Long/Double/Boolean/Float/
        // Short/Byte/Character).
        relationIsEmpty(analysis, "ModelPrim_FAIL_TypeMissing")
    }
}
