package org.clyze.doop.soot;

import soot.PackManager;
import soot.SootClass;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.Set;

public class JimpleGenerator implements Runnable {
    private Set<SootClass> _sootClasses;

    public JimpleGenerator(Set<SootClass> sootClasses) {
        this._sootClasses = sootClasses;
    }

    @Override
    public void run() {
        for (SootClass sootClass : _sootClasses) {
            PackManager.v().writeClass(sootClass);
//  anantoni: Not releasing active bodies should be safer
//            for (SootMethod m : new ArrayList<>(sootClass.getMethods())) {
//                m.releaseActiveBody();
//            }
        }
    }
}
