package org.clyze.doop.soot

import spock.lang.Specification

class SootParametersTest extends Specification {
    def "SootParameters parsing"() {
        given:
        SootParameters sootParameters = new SootParameters()
        String[] args = [
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
        ] as String[]

        when:
        int i1 = sootParameters.processNextArg(args, 0)
        int i2 = sootParameters.processNextArg(args, i1 + 1)
        int i3 = sootParameters.processNextArg(args, i2 + 1)
        int i4 = sootParameters.processNextArg(args, i3 + 1)
        int i5 = sootParameters.processNextArg(args, i4 + 1)
        int i6 = sootParameters.processNextArg(args, i5 + 1)
        int i7 = sootParameters.processNextArg(args, i6 + 1)
        for (int i = i7 + 1; i < args.length; i++) {
            int next_i = sootParameters.processNextArg(args, i);
            i = next_i;
        }

        then:
        1 == i1

        3 == i2
        "Main".equals(sootParameters._main)

        5 == i3
        2 == sootParameters._cores.intValue()

        6 == i4
        true == sootParameters._ignoreWrongStaticness

        7 == i5
        true == sootParameters._generateJimple

        9 == i6
        2 == sootParameters.getInputs().size()
        "a.jar".equals(sootParameters.getInputs().get(0))
        "b.aar".equals(sootParameters.getInputs().get(1))

        2 == sootParameters.getDependencies().size()
        "d1.jar".equals(sootParameters.getDependencies().get(0))
        "d2.apk".equals(sootParameters.getDependencies().get(1))

        3 == sootParameters.getPlatformLibs().size()
        "android.jar".equals(sootParameters.getPlatformLibs().get(0))
        "path/to/layoutlib.jar".equals(sootParameters.getPlatformLibs().get(1))
        "jce.jar".equals(sootParameters.getPlatformLibs().get(2))

        true == sootParameters._ssa

        true == sootParameters._android
        "android.jar".equals(sootParameters._androidJars)
    }
}
