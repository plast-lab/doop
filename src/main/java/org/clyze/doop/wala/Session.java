package org.clyze.doop.wala;

import com.ibm.wala.ssa.SSAInstruction;

import java.util.HashMap;
import java.util.Map;

public class Session
{
    /** keeps the current count of temporary vars of a certain kind, identified by base name. */
    private Map<String, Integer> _tempVarMap = new HashMap<>();

    public int nextNumber(String s)
    {
        Integer x = _tempVarMap.get(s);

        if(x == null)
        {
            x = 0;
        }

        _tempVarMap.put(s, x + 1);

        return x;
    }

    /*
     * We keep 2 indexes for each instruction
     * We do this because assign constant instructions do not exist in WALA and constants are used directly as parameters
     * When an instruction uses one or more constants we create fake assign constant instructions in WalaFactGenerator::generateUses()
     * We use the instruction itself to create the index/instructionNumber of the fake instructions. After the fake assign
     * instructions have been generated, the call to calcInstructionNumber() when generating instruction-specific facts
     * creates the actual index of the instruction. Because this is the last call to calcInstructionNumber() this index
     * will be in _instructionsMaxIndex.
     * When looking for the index of an instruction in most cases we need the "first" index that is stored in _instructions
     * In some cases (ex. when creating ranges of exception handlers) you may need the "last" index.
     *
     * In general it is very important to understand how to correctly use Session when applying changes to the WALA front end
     * Unnecessary calls to calcInstructionNumber() (in contrast with the SOOT front end where they do not affect the facts) can create wrong facts.
     */
    private Map<SSAInstruction, Integer> _instructions = new HashMap<>();
    private Map<SSAInstruction, Integer> _instructionsMaxIndex = new HashMap<>();
    private int index = 0;

    /**
     WARNING: it is important to know when this method should be used instead of getInstructionNumber() or getMaxInstructionNumber()
     Check the detailed comment lines above this method.
     */
    public int calcInstructionNumber(SSAInstruction instruction)
    {
        index++;
        // record the first unit number for this units (to handle jumps)
        _instructions.putIfAbsent(instruction, index);
        _instructionsMaxIndex.put(instruction, index);
        return index;
    }

    public int getInstructionNumber(SSAInstruction instruction)
    {
        Integer result = _instructions.get(instruction);
        if(result == null) {
            throw new RuntimeException("No unit number available for '" + instruction + "'");
        }

        return result;
    }

    public int getMaxInstructionNumber(SSAInstruction instruction)
    {
        Integer result = _instructionsMaxIndex.get(instruction);
        if(result == null) {
            throw new RuntimeException("No unit number available for '" + instruction + "'");
        }

        return result;
    }

}

