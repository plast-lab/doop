package com.foo;

// Referenced ONLY reflectively (by name string) from the app. Its markers tell
// us whether a reflection-only reference pulls the class into the facts.
public class Refl {
    public String whoAmI() {
        return "Refl-BODY";
    }

    public void reflMarker() {
    }
}
