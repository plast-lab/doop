package org.clyze.doop.dex;

import org.jf.dexlib2.dexbacked.value.DexBackedArrayEncodedValue;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.BasicAnnotation;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.iface.value.ArrayEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.util.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

enum DexUtils {
    ;

    private static final int[] EMPTY_REGS_ARRAY = {};

    static String inferArrayTypeForPayload(List<Number> numbers, int index,
                                           int numbersSize, int width) {
        // Heuristic for empty arrays, for completeness.
        if (numbersSize == 0) {
            if (width == 1) return "byte[]";
            else if (width == 2) return "short[]";
            else if (width == 4) return "int[]";
            else if (width == 8) return "long[]";
        } else {
            Number n = numbers.get(0);
            if (n instanceof Byte) return "byte[]";
            else if (n instanceof Double) return "double[]";
            else if (n instanceof Float) return "float[]";
            else if (n instanceof Integer) return "int[]";
            else if (n instanceof Long) return "long[]";
            else if (n instanceof Short) return "short[]";
        }
        System.err.println("Cannot infer array type at instruction " + index);
        return "java.lang.Object[]";
    }

    /**
     * Returns the number of registers that a type needs.
     * @param type   the name of the type
     * @return       the number of registers
     */
    public static int regSizeOf(String type) {
        return TypeUtils.isWideType(type) ? 2 : 1;
    }

    /**
     * Finds the register used in a 5-register or range instruction.
     * @param instr  the instruction
     * @return       an array of register numbers
     */
    static int[] regsFor(Instruction instr) {
        if (instr instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction fri = (FiveRegisterInstruction) instr;
            int regCount = fri.getRegisterCount();
            int[] ret = new int[regCount];
            if (regCount > 0) ret[0] = fri.getRegisterC();
            if (regCount > 1) ret[1] = fri.getRegisterD();
            if (regCount > 2) ret[2] = fri.getRegisterE();
            if (regCount > 3) ret[3] = fri.getRegisterF();
            if (regCount > 4) ret[4] = fri.getRegisterG();
            if (regCount > 5)
                System.err.println("Cannot handle register count: " + regCount);
            return ret;
        } else if (instr instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction rri = (RegisterRangeInstruction)instr;
            int startReg = rri.getStartRegister();
            int regCount = rri.getRegisterCount();
            int[] argRegs = new int[regCount];
            for (int i = 0; i < regCount; i++)
                argRegs[i] = startReg + i;
            return argRegs;
        } else {
            System.err.println("Error: cannot determine argument registers for instruction of type " + instr.getClass());
            return EMPTY_REGS_ARRAY;
        }
    }

    static String jvmTypeOf(ReferenceInstruction instr) {
        return ((TypeReference)instr.getReference()).getType();
    }

    /**
     * Retrieve the values of an annotation that accepts a list of values.
     * @param annotation    the annotation object
     * @param conv          a lambda that converts values to strings
     * @return              the list of string values
     */
    static List<String> getAnnotationValues(BasicAnnotation annotation,
                                            Function<EncodedValue, String> conv) {
        List<String> ret = new ArrayList<>();
        for (AnnotationElement elem : annotation.getElements()) {
            ArrayEncodedValue evs = (DexBackedArrayEncodedValue) elem.getValue();
            for (EncodedValue ev : evs.getValue())
                ret.add(conv.apply(ev));
        }
        return ret;
    }

}
