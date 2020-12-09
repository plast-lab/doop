package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeBT.ExceptionHandler;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*
 * We use this class to take advantage of the information the com.ibm.wala.shrikeBT.ExceptionHandler class offers.
 * For each IBytecodeMethod there is an ExceptionHandler[][] containing an array of ExceptionHandler for each instruction
 * of the instruction array.
 * Using this array we create 2 arrays: exceArrays and exceTypeArrays
 * exceArrays: for each instruction contains an array of int representing the ids of each handler that is active in that instruction
 * exceTypeArrays: for each element of exceArrays contains the String representation of the type of the handler
 *
 * Using exceArrays we can create the scopes of each handler: the scopes are represented in an array of int and the
 * number of scopes is the length of that array divided by two.[0,3,7,12] contains two scopes 0-3 and 7-12
 * The scopes of each handler are stored in walaHandlerScopes
 */

class WalaExceptionHelper {
    private final int[][] exceArrays;
    private final String[][] exceTypeArrays; //We do not currently use this information but we may do so in the future.
    private final SSAInstruction[] instructions;
    private final Map<Integer,Integer[]> walaHandlerScopes;
    private ExceptionHandler[][] walaExceptionHandlers;

    WalaExceptionHelper(SSAInstruction[] instrs, IMethod m, SSACFG cfg)
    {
        IBytecodeMethod<?> bytecodeMethod = (IBytecodeMethod<?>) m;
        walaExceptionHandlers = null;
        try {
            walaExceptionHandlers = bytecodeMethod.getHandlers();
        } catch (InvalidClassFileException e) {
            throw new RuntimeException(e);
        }
        instructions = instrs;
        exceArrays = new int[instructions.length][];
        exceTypeArrays = new String[instructions.length][];
        walaHandlerScopes = new HashMap<>();

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                this.computeArraysForInstruction(j);
            }
        }
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

    private void computeArraysForInstruction(int instrIndex)
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

    private int[] handlersToArray(ExceptionHandler[] handlers)
    {
        if(handlers == null) //Happens in Android
            return new int[0];
        int[] targetsArray = new int[handlers.length];
        for(int i=0; i < handlers.length ; i++)
        {
            targetsArray[i] = handlers[i].getHandler();
        }
        return targetsArray;
    }

    private String[] handlersStringArray(ExceptionHandler[] handlers)
    {
        if(handlers == null) //Happens in Android
            return new String[0];
        String[] targetsArray = new String[handlers.length];
        for(int i=0; i < handlers.length ; i++)
        {
            targetsArray[i] = handlers[i].getCatchClass();
        }
        return targetsArray;
    }

}
