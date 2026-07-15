package com.foo;

// Version 2 of com.foo.Bar. Same class name, different body. Distinguishing
// signals: fieldV2 / markerV2() / the string "Bar-V2". whoAmI() is common.
public class Bar {
    public int fieldV2 = 2;

    public String whoAmI() {
        return "Bar-V2";
    }

    public void markerV2() {
    }
}
