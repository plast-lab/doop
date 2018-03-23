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

    /** keeps the unique index of an instruction in the method. This cannot be computed up front,
     because temporary variables (and assignments to them from constants) will be inserted
     while the Jimple code is being processed. */
    private Map<SSAInstruction, Integer> _instructions = new HashMap<>();
    private int index = 0;

    public int calcInstructionNumber(SSAInstruction instruction)
    {
        index++;

        // record the first unit number for this units (to handle jumps)
        _instructions.putIfAbsent(instruction, index);

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

}

