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
                "--generate-jimple",
                "-i", "a.jar",
                "-i", "b.aar",
                "-ld", "d1.jar",
                "-ld", "d2.apk",
                "-l", "android.jar",
                "-l", "path/to/layoutlib.jar",
                "-l", "jce.jar",
                "--android-jars", "android.jar",
                "--ssa"
        };

        int i1 = sootParameters.processNextArg(args, 0);
        int i2 = sootParameters.processNextArg(args, i1 + 1);
        int i3 = sootParameters.processNextArg(args, i2 + 1);
        int i4 = sootParameters.processNextArg(args, i3 + 1);
        int i5 = sootParameters.processNextArg(args, i4 + 1);
        int i6 = sootParameters.processNextArg(args, i5 + 1);
        int i7 = sootParameters.processNextArg(args, i6 + 1);
        for (int i = i7 + 1; i < args.length; i++) {
            int next_i = sootParameters.processNextArg(args, i);
            assertNotEquals(next_i, -1);
            i = next_i;
        }

        assertEquals(1, i1);

        assertEquals(3, i2);
        assertEquals("Main", sootParameters._main);

        assertEquals(5, i3);
        assertEquals(2, sootParameters._cores.intValue());

        assertEquals(6, i4);
        assertTrue(sootParameters._ignoreWrongStaticness);

        assertEquals(7, i5);
        assertTrue(sootParameters._generateJimple);

        assertEquals(9, i6);
        assertEquals(sootParameters.getInputs().size(), 2);
        assertEquals(sootParameters.getInputs().get(0), "a.jar");
        assertEquals(sootParameters.getInputs().get(1), "b.aar");

        assertEquals(sootParameters.getDependencies().size(), 2);
        assertEquals(sootParameters.getDependencies().get(0), "d1.jar");
        assertEquals(sootParameters.getDependencies().get(1), "d2.apk");

        assertEquals(sootParameters.getPlatformLibs().size(), 3);
        assertEquals(sootParameters.getPlatformLibs().get(0), "android.jar");
        assertEquals(sootParameters.getPlatformLibs().get(1), "path/to/layoutlib.jar");
        assertEquals(sootParameters.getPlatformLibs().get(2), "jce.jar");

        assertTrue(sootParameters._ssa);
        assertTrue(sootParameters._android);
        assertEquals(sootParameters._androidJars, "android.jar");
    }
}