package org.clyze.doop.soot;

import soot.Unit;

import java.util.HashMap;
import java.util.Map;

public class Session
{
  /** keeps the current count of temporary vars of a certain kind, identified by base name. */
  private Map<String, Integer> _tempVarMap = new HashMap<String, Integer>();

  int nextNumber(String s)
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
  private Map<Unit, Integer> _units = new HashMap<Unit, Integer>();
  private int index = 0;

  int calcUnitNumber(Unit u)
  {
    index++;

    // record the first unit number for this units (to handle jumps)
    _units.putIfAbsent(u, index);

    return index;
  }

  int getUnitNumber(Unit u)
  {
    Integer result = _units.get(u);
    if(result == null) {
      throw new RuntimeException("No unit number available for '" + u + "'");
    }

    return result;
  }

}
