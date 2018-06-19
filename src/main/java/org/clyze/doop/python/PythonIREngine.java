package org.clyze.doop.python;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.python.ir.PythonLanguage;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.SeqClassHierarchyFactory;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonIREngine {
    private List<Module> modules;
    private PythonLoaderFactory loader;
    private AnalysisScope scope;
    private IClassHierarchy cha;
    private IRFactory<IMethod> irs;
    private IAnalysisCacheView cache;

    public List<Module> getModules() {
        return modules;
    }

    public PythonLoaderFactory getLoader() {
        return loader;
    }

    public AnalysisScope getAnalysisScope() {
        return scope;
    }

    public IClassHierarchy getClassHierarchy() {
        return cha;
    }

    public IAnalysisCacheView getAnalysisCache() {
        return cache;
    }

    PythonIREngine(List<String> fileNames)
    {
        modules = new ArrayList<>(fileNames.size());
        loader = new PythonLoaderFactory();
        irs = AstIRFactory.makeDefaultFactory();
        cache = new AnalysisCacheImpl(irs);
        for(String file : fileNames) {
            if(file.endsWith(".py")) {
                Path absolutePath = Paths.get(file).toAbsolutePath().normalize();
                try {
                    modules.add(new SourceURLModule(new URL("file://" + absolutePath)));
                }catch(MalformedURLException ex) {
                    System.err.println("Failed to create module for file " + absolutePath + "\nexception:" + ex);
                }
            }
            else
                System.err.println("Unrecognised type of file " + file);
        }
    }

    AnalysisScope buildAnalysisScope() {
        scope = new AnalysisScope(Collections.singleton(PythonLanguage.Python)) {
            {
                loadersByName.put(PythonTypes.pythonLoaderName, PythonTypes.pythonLoader);
                loadersByName.put(SYNTHETIC, new ClassLoaderReference(SYNTHETIC, PythonLanguage.Python.getName(), PythonTypes.pythonLoader));
            }
        };

        for(Module o : modules) {
            scope.addToScope(PythonTypes.pythonLoader, o);
        }
        return scope;
    }

    IClassHierarchy buildClassHierarchy()
    {
        try {
            cha = SeqClassHierarchyFactory.make(scope, loader);
        } catch (ClassHierarchyException e) {
            System.err.println("Exception when creating a Class Hierarchy: "+ e);
            cha = null;
        }
        return cha;
    }
}
