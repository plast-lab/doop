package org.clyze.doop.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by anantoni on 9/3/2017.
 */
public class CustomURLClassLoader extends URLClassLoader {
    public List<String> classesLoaded;

    CustomURLClassLoader(URL[] var1, ClassLoader var2) {
        super(var1, var2);
        classesLoaded = new ArrayList<>();

    }

    public Class<?> loadClass(final String className) throws ClassNotFoundException {
        classesLoaded.add(className);
        Class klass = super.loadClass(className);
        return klass;
    }
}
