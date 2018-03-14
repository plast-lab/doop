package org.clyze.doop.wala;

import com.ibm.wala.types.TypeReference;

public class Local {
    private String name;
    private String sourceCodeName;
    TypeReference type;

    public Local(String name, TypeReference type) {
        this(name, null, type);
    }

    public Local(String name, String sourceCodeName, TypeReference type) {
        this.name = name;
        this.sourceCodeName = sourceCodeName;
        this.type = type;
    }

    public TypeReference getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
