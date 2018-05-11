package org.clyze.doop.wala;

import com.ibm.wala.shrikeBT.ExceptionHandler;
import com.ibm.wala.ssa.SSAInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WalaExceptionHelper {
    int[][] exceArrays;
    String[][] exceTypeArrays;
    SSAInstruction[] instructions;
    Map<Integer,Integer[]> walaHandlerScopes;
    ExceptionHandler[][] walaExceptionHandlers;

    public WalaExceptionHelper(SSAInstruction[] instrs, ExceptionHandler[][] walaExcHandlers)
    {
        walaExceptionHandlers = walaExcHandlers;
        instructions = instrs;
        exceArrays = new int[instructions.length][];
        exceTypeArrays = new String[instructions.length][];
        walaHandlerScopes = new HashMap<>();
    }

    public int getWalaExceptionID(int instrIndex, int exceptionIndex)
    {
        return exceArrays[instrIndex][exceptionIndex];
    }

    public int[] getWalaExceptionIDArray(int instrIndex)
    {
        return exceArrays[instrIndex];
    }

    public String getWalaExceptionType(int instrIndex, int exceptionIndex)
    {
        return exceTypeArrays[instrIndex][exceptionIndex];
    }

    public String[] getWalaExceptionTypeArray(int instrIndex)
    {
        return exceTypeArrays[instrIndex];
    }

    public void computeArraysForInstruction(int instrIndex)
    {
        if(instructions[instrIndex] != null)
        {
            exceArrays[instrIndex] = handlersToArray(walaExceptionHandlers[instrIndex]);
            exceTypeArrays[instrIndex] = handlersStringArray(walaExceptionHandlers[instrIndex]);
        }
        else
        {
            exceArrays[instrIndex] = null;
            exceTypeArrays[instrIndex] = null;
        }
    }

    public Integer[] computeScopeForExceptionHandler(int walaExceptionHandlerID)
    {
        if(walaHandlerScopes.containsKey(walaExceptionHandlerID))
            return walaHandlerScopes.get(walaExceptionHandlerID);

        int scopeStart = -1;
        boolean previous = false;
        boolean found;
        ArrayList<Integer> scopeArrayList = new ArrayList<>();
        for(int i=0; i < exceArrays.length;i++)
        {
            found = false;
            if(exceArrays[i] == null)
                continue;
            for(int j=0; j < exceArrays[i].length; j++) {
                if (walaExceptionHandlerID == exceArrays[i][j]) {
                    if (scopeStart == -1)
                        scopeStart = i;
                    found = true;
                    break;
                }
            }
            if(!found && previous)
            {
                scopeArrayList.add(scopeStart);
                scopeArrayList.add(i);
                scopeStart = -1;
            }
            previous = found;
        }
        if(scopeArrayList.isEmpty())
            return new Integer[0];
        Integer[] scopeArray = new Integer[scopeArrayList.size()];
        scopeArray = scopeArrayList.toArray(scopeArray);
        walaHandlerScopes.put(walaExceptionHandlerID,scopeArray);
        return scopeArray;
    }

    int[] handlersToArray(ExceptionHandler[] handlers)
    {
        int[] targetsArray = new int[handlers.length];
        for(int i=0; i < handlers.length ; i++)
        {
            targetsArray[i] = handlers[i].getHandler();
        }
        return targetsArray;
    }

    String[] handlersStringArray(ExceptionHandler[] handlers)
    {
        String[] targetsArray = new String[handlers.length];
        for(int i=0; i < handlers.length ; i++)
        {
            targetsArray[i] = handlers[i].getCatchClass();
        }
        return targetsArray;
    }

}
