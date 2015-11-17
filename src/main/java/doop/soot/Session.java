package doop.soot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import soot.Unit;

public class Session
{
  /** keeps the current count of temporary vars of a certain kind, identified by base name. */
  private Map<String, Integer> _tempVarMap = new HashMap<String, Integer>();

  public int nextNumber(String s)
  {
    Integer x = _tempVarMap.get(s);

    if(x == null)
    {
      x = Integer.valueOf(0);
    }

    _tempVarMap.put(s, Integer.valueOf(x.intValue() + 1));

    return x.intValue();
  }

  /** keeps the unique index of an instruction in the method. This cannot be computed up front,
      because temporary variables (and assignments to them from constants) will be inserted
      while the Jimple code is being processed. */
  private Map<Unit, Integer> _units = new HashMap<Unit, Integer>();
  private int index = 0;

  public int calcUnitNumber(Unit u)
  {
    index++;

    // record the first unit number for this units (to handle jumps)
    Integer val = _units.get(u);
    if(val == null) {
      _units.put(u, index);
    }

    return index;
  }

  public int getUnitNumber(Unit u)
  {
    Integer result = _units.get(u);
    if(result == null) {
      throw new RuntimeException("No unit number available for '" + u + "'");
    }

    return result;
  }

}
