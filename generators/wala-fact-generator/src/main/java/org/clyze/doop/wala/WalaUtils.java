package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.JavaPrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;

import java.util.Iterator;

public class WalaUtils {

    public static SSAInstruction getNextNonNullInstruction(IR ir, int instructionIndex)
    {
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        ISSABasicBlock basicBlock = cfg.getBlockForInstruction(instructionIndex);
        int bbNum = basicBlock.getNumber();
        int lastbbNum = cfg.getMaxNumber();
        int startIndex;
        int endIndex;
        for(int i=bbNum; i<=lastbbNum; i++){
            ISSABasicBlock currBB = cfg.getBasicBlock(i);
            if(i == bbNum){
                startIndex = instructionIndex + 1;
            }
            else{
                startIndex = currBB.getFirstInstructionIndex();
                Iterator<SSAPhiInstruction> phis = currBB.iteratePhis();
                if(phis.hasNext())
                    return phis.next();
            }
            endIndex = currBB.getLastInstructionIndex();
            for(int j = startIndex; j<= endIndex; j++){
                if(ssaInstructions[j]!=null)
                    return ssaInstructions[j];
            }
        }
//        for(int i = instructionIndex +1 ; i < ssaInstructions.length; i++)
//        {
//            if(ssaInstructions[i]!= null)
//                return ssaInstructions[i];
//        }
        return null;
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
                    System.err.println("WARNING: unknown primitive code found: " + temp);
                    ret = "OTHERPRIMITIVE";
                    break;
            }
            //TODO: Figure out what the 'P' code represents in WALA's TypeReference

        }
        if(isArrayType)
        {
            StringBuilder retBuilder = new StringBuilder(ret);
            for(int i = 0; i < arrayTimes ; i++)
                retBuilder.append("[]");
            ret = retBuilder.toString();
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

    /*
     * Getting methodType (the method descriptor in JVM format) and the name of a method returns the method signature
     * example createMethodSignature("([DLjava/lang/Object;)V", "methName") returns "void methName(double[],java.lang.Object)"
     * Non-primitive types get followed by ';' while primitive types or (arrays of them) get followed by nothing
     * After getting the return type we split the parameter types using ';' each of the split strings can contain
     * many primitive types and one non-primitive type.
     */
    public static String createMethodSignature(String methodType, String methodName)
    {
        StringBuilder signature = new StringBuilder();
        boolean first = true;
        String[] splitType = methodType.substring(1).split("\\)");
        String retType = splitType[1];
        String paramTypes = splitType[0];
        String[] splitParams = paramTypes.split(";");
        int prevArray = 0;

        signature.append(fixTypeString(retType.replace(";","")));
        signature.append(" ");
        signature.append(methodName);
        signature.append("(");

        for(String s: splitParams)
        {
            for(int i = 0; i < s.length();i++)
            {
                char ch = s.charAt(i);
                if(ch == '[')
                {
                    prevArray++;
                }
                else {
                    if(!first)
                        signature.append(",");
                    switch (ch) {
                        case 'L':
                            signature.append(fixTypeString(s.substring(i)));
                            i = s.length();     //When we reach a non-primitive type it can not be followed by anything
                            break;
                        case 'Z':
                            signature.append("boolean");
                            break;
                        case 'I':
                            signature.append("int");
                            break;
                        case 'V':
                            signature.append("void");
                            break;
                        case 'B':
                            signature.append("byte");
                            break;
                        case 'C':
                            signature.append("char");
                            break;
                        case 'D':
                            signature.append("double");
                            break;
                        case 'F':
                            signature.append("float");
                            break;
                        case 'J':
                            signature.append("long");
                            break;
                        case 'S':
                            signature.append("short");
                            break;
                    }
                    if(prevArray != 0)
                        for(int j=0; j < prevArray; j++)
                            signature.append("[]");
                    prevArray=0;
                    if(first)
                        first = false;
                }
            }
        }
        signature.append(")");
        return signature.toString();
    }
}
