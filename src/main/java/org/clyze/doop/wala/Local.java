package org.clyze.doop.wala;

import com.ibm.wala.types.TypeReference;

public class Local {
    private String name;
    private String sourceCodeName;
    TypeReference type;
    String value;

    public Local(String name, TypeReference type) {
        this(name, null, type);
    }

    public Local(String name, String sourceCodeName, TypeReference type) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
    }

    public Local(String name, String sourceCodeName, TypeReference type, String value) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
        this.value = value;
    }

    public TypeReference getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
