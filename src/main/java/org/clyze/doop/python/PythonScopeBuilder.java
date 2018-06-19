package org.clyze.doop.python;

import com.ibm.wala.cast.python.ir.PythonLanguage;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonScopeBuilder {

    public static AnalysisScope buildAnalysisScope(List<String> fileNames) throws IOException {
        AnalysisScope scope = new AnalysisScope(Collections.singleton(PythonLanguage.Python)) {
            {
                loadersByName.put(PythonTypes.pythonLoaderName, PythonTypes.pythonLoader);
                loadersByName.put(SYNTHETIC, new ClassLoaderReference(SYNTHETIC, PythonLanguage.Python.getName(), PythonTypes.pythonLoader));
            }
        };

        List<Module> modules = new ArrayList<>(fileNames.size());
        for(String file : fileNames) {
            if(file.endsWith(".py"))
                modules.add(new SourceURLModule(new URL(file)));
            else
                System.err.println("Unrecognised type of file " + file);
        }
        for(Module o : modules) {
            scope.addToScope(PythonTypes.pythonLoader, o);
        }
        return scope;
    }
}
