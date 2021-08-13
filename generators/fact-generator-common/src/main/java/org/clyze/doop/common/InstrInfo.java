package org.clyze.doop.common;

/**
 * This data structure holds metadata about an instruction/statement.
 */
public class InstrInfo {
    /** The index of the instruction in the method body. */
    public final int index;
    /** The instruction id. */
    public final String insn;
    /** The containing method id. */
    public final String methodId;

    /**
     * Main constructor.
     * @param methodId     the containing method
     * @param insn         the unique instruction id
     * @param index        the instruction index (method body position)
     */
    public InstrInfo(String methodId, String insn, int index) {
        this.methodId = methodId;
        this.index = index;
        this.insn = insn;
    }

    /**
     * Alternative constructor for pseudo instructions.
     * @param methodId     the containing method
     * @param kind         the kind of the instruction
     * @param session      the session object
     */
    public InstrInfo(String methodId, String kind, SessionCounter session) {
        // Use arbitrary new Object to increase instruction counter.
        this(methodId, JavaRepresentation.instructionId(methodId, kind, session.nextNumber(kind)), session.calcInstructionIndex(new Object()));
    }

    @Override
    public String toString() {
        return "InstrInfo{ index=" + index + ", insn=" + insn + ", methodId=" + methodId + " }";
    }
}
