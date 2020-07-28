package org.clyze.doop.soot;

import java.util.Set;
import soot.Scene;
import soot.SootClass;

public interface ClassAdder {
    void addAppClasses(Set<SootClass> classes, Scene scene);
    void addLibClasses(Set<SootClass> classes, Scene scene);
    void addDepClasses(Set<SootClass> classes, Scene scene);
    boolean isAppClass(String t);
    boolean isLibClass(String t);
    boolean isAppOrDepClass(String t);
}
