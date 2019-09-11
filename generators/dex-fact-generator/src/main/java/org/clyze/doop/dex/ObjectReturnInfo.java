package org.clyze.doop.dex;

import org.checkerframework.checker.nullness.qual.*;
import org.jf.dexlib2.Opcode;

import java.util.Arrays;

/**
 * This class records information about objects that are returned either from method
 * invocations or from array construction (FILLED_NEW_ARRAY/FILLED_NEW_ARRAY_RANGE
 * opcodes).
 */
class ObjectReturnInfo {
    public final @Nullable Integer baseReg;
    public final int[] argRegs;
    public final String insn;
    public final Opcode op;
    public final int index;
    public final String retType;

    /**
     * Record information about an object that is returned from a method invocation or
     * array creation opcode.
     * @param insn        the instruction returning the object (String id)
     * @param argRegs0    the argument registers
     * @param retType     the return type (method signature information or array signature)
     * @param frmCount    the number of parameters (in method signature information or array signature)
     * @param isStatic    if this is a static method call (always true for array creation)
     * @param op          the instruction returning the object (opcode)
     */
    ObjectReturnInfo(String insn, int[] argRegs0, String retType, int frmCount,
                     boolean isStatic, Opcode op, int index) {
        this.insn = insn;
        this.op = op;
        this.index = index;
        this.retType = retType;

        // Calculate registers.
        int argStartIdx = 0;
        // Consume one register for "this" if the function is not static
        this.baseReg = (isStatic) ? null : argRegs0[argStartIdx++];
        // Put the rest of the registers into argRegs
        int argCount = argRegs0.length - argStartIdx;
        if (frmCount > argCount)
            throw new RuntimeException("Cannot find all " + frmCount + " arguments, only " + argCount + " are available.");
        this.argRegs = new int[frmCount];
        System.arraycopy(argRegs0, argStartIdx, this.argRegs, 0, frmCount);
    }

    @Override
    public String toString() {
        return ("{ opcode: " + op + ", baseReg: " + baseReg + ", argRegs: " + Arrays.toString(argRegs) + ", retType: " + retType + ", insn: " + insn + ", index: " + index + " }");
    }
}
