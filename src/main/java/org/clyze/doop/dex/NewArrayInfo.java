package org.clyze.doop.dex;

class NewArrayInfo {
    final int index;
    final String heapId;

    /**
     * Information about a processed new-array instruction.
     *
     * @param index    the instruction index
     * @param heapId   the id of the array heap created
     */
    NewArrayInfo(int index, String heapId) {
        this.index = index;
        this.heapId = heapId;
    }
}
