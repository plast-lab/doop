package org.clyze.doop.soot

import spock.lang.Specification

class SootParametersTest extends Specification {
    def "SootParameters parsing"() {
        given:
        String[] args = [
            "--application-regex", "XYZ",
            "--main", "Main",
            "--fact-gen-cores", "2",
            "--ignore-wrong-staticness",
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

        "Main" == sootParameters._main

        2 == sootParameters._cores.intValue()

        true == sootParameters._ignoreWrongStaticness

        true == sootParameters._generateJimple

        2 == sootParameters.inputs.size()
        "a.jar" == sootParameters.inputs.get(0)
        "b.aar" == sootParameters.inputs.get(1)

        2 == sootParameters.dependencies.size()
        "d1.jar" == sootParameters.dependencies.get(0)
        "d2.apk" == sootParameters.dependencies.get(1)

        3 == sootParameters.platformLibs.size()
        "android.jar" == sootParameters.platformLibs.get(0)
        "path/to/layoutlib.jar" == sootParameters.platformLibs.get(1)
        "jce.jar" == sootParameters.platformLibs.get(2)

        true == sootParameters._ssa

        true == sootParameters._android
        "android.jar" == sootParameters._androidJars

        "out-dir" == sootParameters.outputDir
    }
}
