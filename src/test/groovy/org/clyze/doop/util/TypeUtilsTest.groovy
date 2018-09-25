package org.clyze.doop.util

import spock.lang.Specification

class TypeUtilsTest extends Specification {
    def "RaiseTypeId"(String arg, String res) {
        expect:
        TypeUtils.raiseTypeId(arg) == res
        where:
            arg                                                 | res
            "I"                                                 | "int"
            "[I"                                                | "int[]"
            'Landroid/annotation/SdkConstant$SdkConstantType;'  | 'android.annotation.SdkConstant$SdkConstantType'
            "Ljava/lang/Object;"                                | "java.lang.Object"
            "[Ljava/lang/Object;"                               | "java.lang.Object[]"
            "[[Ljava/lang/Object;"                              | "java.lang.Object[][]"

    }

    def "IsPrimitiveType"(String t, boolean b) {
        expect:
            TypeUtils.isPrimitiveType(t) == b
        where:
            t                       | b
            "boolean"               | true
            "float"                 | true
            "double"                | true
            "int"                   | true
            "int[]"                 | false
            "short"                 | true
            "char"                  | true
            "java.lang.Character"   | false
            "S"                     | false
    }
}
