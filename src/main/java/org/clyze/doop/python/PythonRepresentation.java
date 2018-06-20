package org.clyze.doop.python;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PythonRepresentation {
    private Map<String, String> _methodSigRepr = new ConcurrentHashMap<>();

    /*
     * Each catch instruction is identified by the combination of: the method signature of the method it is in,
     * the ir variable def'ed by it and the scope number (to cover cases with multiple scopes for one catch, more right below)
     */
    private Map<String, String> _catchRepr = new ConcurrentHashMap<>();
    /*
     * For each handler that has more than one scope the number of scopes are stored on a map because they can be useful
     * Each different scope of a handler is represented by a different Exception_Handler fact
     * We use it when we need to produce Exception_Handler_Previous facts and need to find the last exception handler of a block
     */
    private Map<String, Integer> _handlerNumOfScopes = new ConcurrentHashMap<>();

    // Make it a trivial singleton.
    private static PythonRepresentation _repr;
    private PythonRepresentation() {}

    static PythonRepresentation getRepresentation() {
        if (_repr == null)
            _repr = new PythonRepresentation();
        return _repr;
    }
}
