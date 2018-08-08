package org.clyze.doop.soot;

import soot.Unit;

import java.util.HashMap;
import java.util.Map;

import org.clyze.doop.SessionCounter;

public class Session extends SessionCounter {

  /** keeps the unique index of an instruction in the method. This cannot be computed up front,
      because temporary variables (and assignments to them from constants) will be inserted
      while the Jimple code is being processed. */
  private Map<Unit, Integer> _units = new HashMap<Unit, Integer>();
  private int index = 0;

  public int calcUnitNumber(Unit u)
  {
    index++;

    // record the first unit number for this units (to handle jumps)
    _units.putIfAbsent(u, index);

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
