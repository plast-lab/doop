package org.clyze.doop.wala;

import com.ibm.wala.types.TypeReference;

public class Local {
    private String name;
    private String sourceCodeName;
    TypeReference type;
    String value;
    int varIndex;

    Local(String name, int varIndex, TypeReference type) {
        this(name, varIndex, null, type);
    }

    Local(String name, int varIndex, String sourceCodeName, TypeReference type) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
        this.varIndex = varIndex;
    }

    public int getVarIndex() {
        return varIndex;
    }

    public Local(String name, int varIndex, String sourceCodeName, TypeReference type, String value) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
        this.value = value;
        this.varIndex = varIndex;
    }

    public TypeReference getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
