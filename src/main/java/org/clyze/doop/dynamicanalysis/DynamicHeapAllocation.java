package org.clyze.doop.dynamicanalysis;

/**
 * Created by neville on 15/02/2017.
 */
public interface DynamicHeapAllocation extends DynamicFact {
    String getRepresentation();

    boolean isProbablyUnmatched();
}
