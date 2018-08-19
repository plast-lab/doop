package org.clyze.doop.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;

/**
 * This class contains common parameters for Doop Java front-ends.
 */
public abstract class Parameters {
    protected List<String> _inputs = new ArrayList<>();
    protected List<String> _libraries = new ArrayList<>();
    protected String _outputDir = null;
    public String _extraSensitiveControls = "";
    private String appRegex;
    public ClassFilter applicationClassFilter;
    public boolean _android = false;
    public Integer _cores = null;
    public String _androidJars = null;
    public String _seed = null;
    public String _specialCSMethods = null;

    public Parameters() {
	setAppRegex("**");
    }

    public void setAppRegex(String regex) {
        this.appRegex = regex;
        this.applicationClassFilter = new GlobClassFilter(this.appRegex);
    }

    public void setInputs(List<String> inputs) {
        this._inputs = inputs;
    }

    public List<String> getInputs() {
        return this._inputs;
    }

    public void setLibraries(List<String> libraries) {
        this._libraries = libraries;
    }

    public List<String> getLibraries() {
        return this._libraries;
    }

    public void setOutputDir(String outputDir) {
        this._outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }

    public void processSeeds(JavaFactWriter writer) throws IOException {
        if (_seed != null) {
            try (Stream<String> stream = Files.lines(Paths.get(_seed))) {
                stream.forEach(line -> processSeedFileLine(line, writer));
            }
        }
    }

    private static void processSeedFileLine(String line, JavaFactWriter writer) {
        if (line.contains("("))
            writer.writeAndroidKeepMethod(line);
        else if (!line.contains(":"))
            writer.writeAndroidKeepClass(line);
    }

    public void processSpecialCSMethods(JavaFactWriter writer) throws IOException {
        if (_specialCSMethods != null)
            try (Stream<String> stream = Files.lines(Paths.get(_specialCSMethods))) {
                stream.forEach(line -> processSpecialSensitivityMethodFileLine(line, writer));
            }
    }

    private static void processSpecialSensitivityMethodFileLine(String line, JavaFactWriter writer) {
        if (line.contains(", "))
            writer.writeSpecialSensitivityMethod(line);
    }

    public boolean isApplicationClass(String className) {
        return applicationClassFilter.matches(className);
    }

    public abstract List<String> getInputsAndLibraries();

}
