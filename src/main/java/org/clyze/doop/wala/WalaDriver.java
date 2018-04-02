package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WalaDriver {

    void doSequentially(Iterator<IClass> iClasses, WalaFactWriter writer, String outDir) {
        WalaFactGenerator factGenerator = new WalaFactGenerator(writer, iClasses, outDir);
        //factGenerator.generate(dummyMain, new Session());
        //writer.writeAndroidEntryPoint(dummyMain);
        factGenerator.run();
    }
}
