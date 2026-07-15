package com.foo;

// Third-level class, referenced only from com.foo.Baz's BODY. Its presence in
// the facts tells us Soot followed Baz's body references (closure depth).
public class Deep {
    public String whoAmI() {
        return "Deep-BODY";
    }

    public void deepMarker() {
    }
}
