package org.clyze.doop.wala;

import com.ibm.wala.types.TypeReference;

public class Local {
    private String name;
    private String sourceCodeName;
    TypeReference type;
    String value;
    int varIndex;

    public Local(String name, int varIndex, TypeReference type) {
        this(name, varIndex, null, type);
    }

    public Local(String name, int varIndex, String sourceCodeName, TypeReference type) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
        this.varIndex = varIndex;
        this.value = null;
    }

    public int getVarIndex() {
        return varIndex;
    }

    public void setType(TypeReference newType)
    {
        type = newType;
    }
    public Local(String name, int varIndex, String sourceCodeName, TypeReference type, String value) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
        this.value = value;
        this.varIndex = varIndex;
    }

    public void setValue(String _value)
    {
        value = _value;
    }

    public String getValue()
    {
        return value;
    }

    public TypeReference getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getSourceName() {
        return sourceCodeName;
    }

    public void setSourceName(String sourceName){
        sourceCodeName = sourceName;
    }

}
