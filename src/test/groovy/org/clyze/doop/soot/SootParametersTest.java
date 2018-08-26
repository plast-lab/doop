package org.clyze.doop.soot;

import org.clyze.doop.common.DoopErrorCodeException;
import org.junit.Test;

import static org.junit.Assert.*;

public class SootParametersTest {

    @Test
    public void processNextArg() throws DoopErrorCodeException {
        SootParameters sootParameters = new SootParameters();
        String[] args = new String[] {
                "--application-regex", "XYZ",
                "--main", "Main",
                "--fact-gen-cores", "2",
                "--ignoreWrongStaticness",
                "--generate-jimple"
        };
        int i1 = sootParameters.processNextArg(args, 0);
        int i2 = sootParameters.processNextArg(args, i1 + 1);
        int i3 = sootParameters.processNextArg(args, i2 + 1);
        int i4 = sootParameters.processNextArg(args, i3 + 1);
        int i5 = sootParameters.processNextArg(args, i4 + 1);

        assertEquals(1, i1);

        assertEquals(3, i2);
        assertEquals("Main", sootParameters._main);

        assertEquals(5, i3);
        assertEquals(2, sootParameters._cores.intValue());

        assertEquals(6, i4);
        assertEquals(true, sootParameters._ignoreWrongStaticness);

        assertEquals(7, i5);
        assertEquals(true, sootParameters._generateJimple);
    }
}