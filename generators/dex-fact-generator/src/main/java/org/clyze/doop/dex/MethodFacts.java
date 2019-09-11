package org.clyze.doop.dex;

class MethodFacts {
    public final String simpleName;
    public final String paramsSig;
    public final String declaringClass;
    public final String retType;
    public final String jvmSig;
    public final String arity;

    MethodFacts(String simpleName, String paramsSig, String declaringClass,
                String retType, String jvmSig, String arity) {
        this.simpleName = simpleName;
        this.paramsSig = paramsSig;
        this.declaringClass = declaringClass;
        this.retType = retType;
        this.jvmSig = jvmSig;
        this.arity = arity;
    }

    public String getMethodId() {
        return DexRepresentation.methodId(declaringClass, retType, simpleName, paramsSig);
    }
}
