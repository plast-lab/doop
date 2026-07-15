package com.foo;

// Library class referenced by the app. Resolution-level markers:
//   - field  bazField          -> present at SIGNATURES or deeper
//   - method whoAmI()          -> signature present at SIGNATURES or deeper
//   - string "Baz-BODY"        -> present only if the whoAmI() BODY is generated
//   - touchDeep() references com.foo.Deep in its BODY -> if Baz bodies are
//     generated, Soot should follow this to Deep (closure depth probe)
public class Baz {
    public int bazField = 7;

    public String whoAmI() {
        return "Baz-BODY";
    }

    public void touchDeep() {
        com.foo.Deep d = new com.foo.Deep();
        d.deepMarker();
    }
}
