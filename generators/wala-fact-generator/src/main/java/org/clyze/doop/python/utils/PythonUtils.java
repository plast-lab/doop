package org.clyze.doop.python.utils;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.wala.Local;

public class PythonUtils {
    public static String fixType(TypeReference type)
    {
        return type.getName().toString().substring(1);
    }

    public static String fixNewType(IMethod inMethod, SSANewInstruction instruction, TypeReference type)
    {
        String typeString = fixType(type);
        if(typeString.startsWith("script ")){
            typeString = typeString.replace("script ", "");
            String scriptName = typeString.substring(0, typeString.indexOf('/'));
            String sourceFileName = inMethod.getDeclaringClass().getSourceFileName();
            if(sourceFileName.contains(scriptName)){
                typeString = sourceFileName.concat(typeString.substring(typeString.indexOf('/')).replace("/",":"));
            }else{
                throw new RuntimeException("Unexpected new "+ instruction);
            }
        }
        return "<" + typeString + ">";
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
            typeRef = PythonTypes.object;
        }
        else {                                            // All other cases - including primitives - should be handled by getting the TypeReference
            typeRef = typeAbstraction.getTypeReference();
            if (typeRef == null) {                        // anantoni: In this case we have encountered WalaTypeAbstraction.TOP
                typeRef = PythonTypes.Root;   // TODO: we don't know what type to give for TOP
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
}
