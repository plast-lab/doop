package org.clyze.doop.dex;

public enum InferType {
    BYTE("byte"),
    CHAR("char"),
    SHORT("short"),
    INT("int"),
    INT32("int32"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    OBJ("obj"),
    PRIM32("prim32"),
    PRIM64("prim64"),
    BITS32("32bit"),
    BITS64("64bit");

    private final String repr;
    InferType(String r) { this.repr = r; }

    @Override public String toString() { return repr; }
}
