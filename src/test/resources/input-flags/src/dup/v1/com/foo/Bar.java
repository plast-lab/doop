package com.foo;

// Version 1 of com.foo.Bar. Distinguishing signals for the experiment:
//   - field   fieldV1     -> appears in Field.facts if fields are emitted
//   - method  markerV1()  -> appears in Method.facts if method sigs are emitted
//   - the string "Bar-V1" -> appears in StringConstant facts only if the
//                            whoAmI() BODY is generated (reveals resolution level)
// whoAmI() is common to both versions, so Main can bind to it regardless of
// which Bar is loaded.
public class Bar {
    public int fieldV1 = 1;

    public String whoAmI() {
        return "Bar-V1";
    }

    public void markerV1() {
    }
}
