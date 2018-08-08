package org.clyze.doop;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.FactEncoders;
import static org.clyze.doop.common.PredicateFile.*;

/**
 * Common functionality that a fact writer for Java bytecode can reuse.
 */
public class JavaFactWriter {

    protected Database _db;

    public JavaFactWriter(Database db) {
        this._db = db;
    }

    protected String str(int i) {
        return String.valueOf(i);
    }

    protected String writeStringConstant(String constant) {
        String raw = FactEncoders.encodeStringConstant(constant);

        String result;
        if(raw.length() <= 256)
            result = raw;
        else
            result = "<<HASH:" + raw.hashCode() + ">>";

        _db.add(STRING_RAW, result, raw);
        _db.add(STRING_CONST, result);

        return result;
    }

    protected String hashMethodNameIfLong(String methodRaw) {
        if (methodRaw.length() <= 1024)
            return methodRaw;
        else
            return "<<METHOD HASH:" + methodRaw.hashCode() + ">>";
    }

    public void writeClassArtifact(String artifact, String className, String subArtifact) {
        _db.add(CLASS_ARTIFACT, artifact, className, subArtifact);
    }

    public void writeAndroidKeepMethod(String methodSig) {
        _db.add(ANDROID_KEEP_METHOD, "<" + methodSig + ">");
    }

    public void writeAndroidKeepClass(String className) {
        _db.add(ANDROID_KEEP_CLASS, className);
    }

    public void writeProperty(String path, String key, String value) {
        String pathId = writeStringConstant(path);
        String keyId = writeStringConstant(key);
        String valueId = writeStringConstant(value);
        _db.add(PROPERTIES, pathId, keyId, valueId);
    }

    public void writeSpecialSensitivityMethod(String line) {
        String[] linePieces = line.split(", ");
        String method = linePieces[0].trim();
        String sensitivity = linePieces[1].trim();

        _db.add(SPECIAL_CONTEXT_SENSITIVITY_METHOD, method, sensitivity);
    }

    protected void writeMethodHandleConstant(String heap, String handleName) {
        _db.add(METHOD_HANDLE_CONSTANT, heap, handleName);
    }

    protected void writeFormalParam(String methodId, String var, String type, int i) {
        _db.add(FORMAL_PARAM, str(i), methodId, var);
        _db.add(VAR_TYPE, var, type);
        _db.add(VAR_DECLARING_METHOD, var, methodId);
    }
}
