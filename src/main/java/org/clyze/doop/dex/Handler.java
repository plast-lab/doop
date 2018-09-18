package org.clyze.doop.dex;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class Handler {
    private final int startAddr;
    private final int endAddr;
    private final int handlerAddr;
    final String excType;

    Handler(int startAddr, int endAddr, int handlerAddr, String excType) {
        this.startAddr = startAddr;
        this.endAddr = endAddr;
        this.handlerAddr = handlerAddr;
        this.excType = excType;
    }

    public Integer getStartIndex(Map<Integer, Integer> addressToIndex) throws Handler.IndexException {
        Integer startIndex = addressToIndex.get(startAddr);
        if (startIndex == null)
            throw new Handler.IndexException("Error: could not find start index for handler: " + this);
        return startIndex;
    }

    public Integer getEndIndex(Map<Integer, Integer> addressToIndex) throws Handler.IndexException {
        Integer endIndex = addressToIndex.get(endAddr);
        if (endIndex == null)
            throw new Handler.IndexException("Error: could not find end index for handler: " + this);
        return endIndex;
    }

    public Integer getIndex(Map<Integer, Integer> addressToIndex) throws Handler.IndexException {
        Integer index = addressToIndex.get(handlerAddr);
        if (index == null)
            throw new Handler.IndexException("Error: could not find handler index for handler: " + this);
        return index;
    }

    static List<Handler> findHandlerStartingAt(Collection<Handler> handlers, int addr) {
        return handlers.stream().filter((Handler hi) -> hi.handlerAddr == addr).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "{" + handlerAddr + ": [" + startAddr + "..." + endAddr + "], exception type: " + excType + "}";
    }

    static class IndexException extends Exception {
        IndexException(String msg) { super(msg); }
    }
}
