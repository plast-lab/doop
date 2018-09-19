package org.clyze.doop.soot

import spock.lang.Specification

class SootParametersTest extends Specification {
    def "SootParameters parsing"() {
        given:
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
            "--ssa",
            "-d", "out-dir"
        ] as String[]
        SootParameters sootParameters = new SootParameters()

        when:
        sootParameters.initFromArgs(args)

        then:

        "Main".equals(sootParameters._main)

        2 == sootParameters._cores.intValue()

        true == sootParameters._ignoreWrongStaticness

        true == sootParameters._generateJimple

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

        "out-dir".equals(sootParameters.getOutputDir())
    }
}
