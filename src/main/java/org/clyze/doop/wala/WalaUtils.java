package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.JavaPrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.TypeReference;

public class WalaUtils {

    public static int getNextNonNullInstruction(IR ir, int instructionIndex)
    {
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        //ISSABasicBlock basicBlock = ir.getBasicBlockForInstruction(ssaInstructions[instructionIndex]);
        for(int i = instructionIndex +1 ; i < ssaInstructions.length; i++)
        {
            if(ssaInstructions[i]!=null)
                return i;
        }
        return -1;
    }

    public static Local createLocal(IR ir, SSAInstruction instruction, int varIndex, TypeInference typeInference) {
        Local l;

        TypeReference typeRef;
        TypeAbstraction typeAbstraction;
        if(typeInference == null)   // TypeInference can be null if we catch an error during it's creation, consider all types Object
            typeAbstraction = null;
        else
            typeAbstraction = typeInference.getType(varIndex);
        if (typeAbstraction == null) {                    // anantoni: TypeAbstraction == null means undefined type
            typeRef = TypeReference.JavaLangObject;
        }
        else {                                            // All other cases - including primitives - should be handled by getting the TypeReference
            typeRef = typeAbstraction.getTypeReference();
            if (typeRef == null) {                        // anantoni: In this case we have encountered WalaTypeAbstraction.TOP
                typeRef = TypeReference.JavaLangObject;   // TODO: we don't know what type to give for TOP
            }
        }
        if (instruction.iindex != -1) {
            String[] localNames = ir.getLocalNames(instruction.iindex, varIndex);
            if (localNames != null && localNames.length != 0) {
                l = new Local("v" + varIndex, varIndex, localNames[0], typeRef);
            }
            else {
                l = new Local("v" + varIndex, varIndex, typeRef);
            }
        }
        else {
            l = new Local("v" + varIndex, varIndex, typeRef);
        }
        if(ir.getSymbolTable().isConstant(varIndex) && ! ir.getSymbolTable().isNullConstant(varIndex))
            l.setValue(ir.getSymbolTable().getConstantValue(varIndex).toString());

        return l;
    }

    public static Local createLocal(IR ir, SSAInstruction instruction, int varIndex) {
        Local l;
        String[] localNames = ir.getLocalNames(instruction.iindex, varIndex);

        if (localNames != null && localNames.length != 0) {
            assert localNames.length == 1;
            l = new Local("v" + varIndex, varIndex, localNames[0],TypeReference.JavaLangObject);
        }
        else {
            l = new Local("v" + varIndex, varIndex, TypeReference.JavaLangObject);
        }
        return l;
    }


    public static String fixTypeString(String original)
    {
        boolean isArrayType = false;
        int arrayTimes = 0;
        String ret;

        if(original.contains("L")) {
            if (original.contains("[")) //Figure out if this is correct
            {
                isArrayType = true;
                for (int i = 0; i < original.length(); i++) {
                    if (original.charAt(i) == '[')
                        arrayTimes++;
                }
            }
            ret = original.substring(original.indexOf("L") + 1).replaceAll("/", ".").replaceAll(">", "");
        }
        else {
            String temp;
            temp = original.substring(original.indexOf(",") + 1).replaceAll(">", "");
            if (temp.startsWith("[")) {
                isArrayType = true;
                for (int i = 0; i < temp.length(); i++) {
                    if (temp.charAt(i) == '[')
                        arrayTimes++;
                    else
                        break;

                }
                temp = temp.substring(arrayTimes);
            }
            switch (temp) {
                case "Z":
                    ret = "boolean";
                    break;
                case "I":
                    ret = "int";
                    break;
                case "V":
                    ret = "void";
                    break;
                case "B":
                    ret = "byte";
                    break;
                case "C":
                    ret = "char";
                    break;
                case "D":
                    ret = "double";
                    break;
                case "F":
                    ret = "float";
                    break;
                case "J":
                    ret = "long";
                    break;
                case "S":
                    ret = "short";
                    break;
                default:
                    ret = "OTHERPRIMITIVE";
                    break;
            }
            //TODO: Figure out what the 'P' code represents in WALA's TypeReference

        }
        if(isArrayType)
        {
            for(int i=0 ; i< arrayTimes ; i++)
                ret = ret + "[]";
        }
        //if(! ret.equals(fixTypeStringOld(original)) && ! original.contains("["))
        //System.out.println(original + " | " + ret + " | " + fixTypeStringOld(original));
        return ret;
    }

    //Alternative to createLocal, to be deleted soon
    public static Local createLocal2(IR ir, SSAInstruction instruction, int varIndex, TypeInference typeInference) {
        Local l;
        String[] localNames ;
        if(instruction.iindex == -1)//Instructions not on the normal instructions array of the IR can have iindex==-1 ex SSAGetCaughtExceptionInstruction, SSAPhiInstruction
            localNames = null;
        else
            localNames = ir.getLocalNames(instruction.iindex, varIndex);

        TypeReference typeRef;
        TypeAbstraction typeAbstraction = typeInference.getType(varIndex);
        if(typeAbstraction.getType() == null && !(typeAbstraction instanceof JavaPrimitiveType))
            typeRef = TypeReference.JavaLangObject;
        else
            typeRef = typeAbstraction.getTypeReference();

        if (localNames != null && localNames.length != 0) {
            l = new Local("v" + varIndex, varIndex, localNames[0], typeRef);
        }
        else {
            l = new Local("v" + varIndex, varIndex, typeRef);
        }
        if(ir.getSymbolTable().isConstant(varIndex) && ! ir.getSymbolTable().isNullConstant(varIndex))
            l.setValue(ir.getSymbolTable().getConstantValue(varIndex).toString());
        return l;
    }
}
