package org.clyze.doop.soot;

import java.util.Collection;
import org.clyze.doop.common.DoopErrorCodeException;

public class MissingClassesException extends DoopErrorCodeException {
    public String[] classes;

    public MissingClassesException(String[] classes) {
        super(21);
        this.classes = classes;
    }
}
