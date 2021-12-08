package org.clyze.doop.common;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.log4j.Logger;
import org.clyze.doop.common.scanner.antlr.GenericTypeLexer;
import org.clyze.doop.common.scanner.antlr.GenericTypeParser;
import org.clyze.doop.common.scanner.antlr.PrintVisitor;

import static org.clyze.doop.common.PredicateFile.*;

/**
 * Common functionality that a fact writer for Java bytecode can reuse.
 */
public abstract class JavaFactWriter {

    protected static final String L_OP = "1";
    protected static final String R_OP = "2";
    protected final Database _db;
    protected final boolean _extractMoreStrings;
    private final boolean _writeArtifactsMap;
    private final boolean _regMethods;
    private final Set<String> methodStrings;
    private final Logger logger = Logger.getLogger(getClass());

    protected JavaFactWriter(Database db, Parameters params) {
        this._db = db;
        this._extractMoreStrings = params._extractMoreStrings;
        this._writeArtifactsMap = params._writeArtifactsMap;
        this._regMethods = params._scanNativeCode;
        this.methodStrings = _regMethods ? ConcurrentHashMap.newKeySet() : null;
    }

    public Set<String> getMethodStrings() {
        return methodStrings;
    }

    public static String str(int i) {
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

    @SuppressWarnings("unused")
    protected String hashMethodNameIfLong(String methodRaw) {
        if (methodRaw.length() <= 1024)
            return methodRaw;
        else
            return "<<METHOD HASH:" + methodRaw.hashCode() + ">>";
    }

    private void writeClassArtifact(String artifact, String className, String subArtifact, int size) {
        _db.add(CLASS_ARTIFACT, artifact, className, subArtifact, str(size));
    }

    private void writeProperty(String path, String key, String value) {
        String pathId = writeStringConstant(path);
        String keyId = writeStringConstant(key);
        String valueId = writeStringConstant(value);
        _db.add(PROPERTIES, pathId, keyId, valueId);
    }

    protected void writeMethodHandleConstant(String heap, String method,
                                             String retType, String paramTypes,
                                             int arity) {
        _db.add(METHOD_HANDLE_CONSTANT, heap, method, retType, paramTypes, str(arity));
    }

    protected void writeFormalParam(String methodId, String var, String type, int i) {
        _db.add(FORMAL_PARAM, str(i), methodId, var);
        writeLocal(var, type, methodId);
    }

    protected void writeThisVar(String methodId, String thisVar, String type) {
        _db.add(THIS_VAR, methodId, thisVar);
        writeLocal(thisVar, type, methodId);
    }

    public void writeApplication(String applicationName) {
        _db.add(ANDROID_APPLICATION, applicationName);
    }

    public void writeActivity(String activity) {
        _db.add(ACTIVITY, activity);
    }

    public void writeService(String service) {
        _db.add(SERVICE, service);
    }

    public void writeContentProvider(String contentProvider) {
        _db.add(CONTENT_PROVIDER, contentProvider);
    }

    public void writeBroadcastReceiver(String broadcastReceiver) {
        _db.add(BROADCAST_RECEIVER, broadcastReceiver);
    }

    public void writeAndroidCallbackMethodName(String callbackMethodName) {
        _db.add(ANDROID_CALLBACK_METHOD_NAME, callbackMethodName);
    }

    public void writeLayoutControl(Integer id, String viewClassName, Integer parentID, String appRId, String androidRId) {
        _db.add(LAYOUT_CONTROL, id.toString(), viewClassName, parentID.toString());
    }

    public void writeSensitiveLayoutControl(Integer id, String viewClassName, Integer parentID) {
        _db.add(SENSITIVE_LAYOUT_CONTROL, id.toString(), viewClassName, parentID.toString());
    }

    public void writeNumConstantRaw(String val, String valType) {
        _db.add(NUM_CONSTANT_RAW, val, valType);
    }

    /**
     * Writes preliminary facts (properties, XML data), which do not need
     * parsing code inputs.
     * @param java     the Java support object
     * @param debug    debug flag
     */
    public void writePreliminaryFacts(BasicJavaSupport java, boolean debug) {
        PropertyProvider propertyProvider = java.getPropertyProvider();

        // Read all stored properties files
        for (Map.Entry<String, Properties> entry : propertyProvider.getProperties().entrySet()) {
            String path = entry.getKey();
            Properties properties = entry.getValue();

            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                writeProperty(path, propertyName, propertyValue);
            }
        }

        generateFactsForXML(_db, java.xmlRoots, debug);
    }

    /**
     * Translate XML files to facts.
     *
     * @param db        the database object to use for output
     * @param xmlRoots  the root directories containing XML files
     * @param debug     debug flag
     */
    private void generateFactsForXML(Database db, Iterable<String> xmlRoots,
                                     boolean debug) {
        // The output directory (the parent of the "decode" directories)
        String outDir = db.getDirectory();
        for (String xmlRoot : xmlRoots) {
            logger.info("Processing XML files in directory: " + xmlRoot);
            XMLFactGenerator.processDir(new File(xmlRoot), db, outDir, debug);
        }
    }

    /**
     * Last step of writing facts, after all classes have been processed.
     *
     * @param java  the object supporting basic Java functionality
     */
    public void writeLastFacts(BasicJavaSupport java) {
        ArtifactScanner artScanner = java.getArtifactScanner();
        Map<String, Set<ArtifactEntry>> artifactToClassMap = artScanner.getArtifactToClassMap();
        Set<GenericFieldInfo> genericFields = artScanner.getGenericFields();
        if (_writeArtifactsMap) {
            System.out.println("Generated artifact-to-class map for " + artifactToClassMap.size() + " artifacts.");
            for (String artifact : artifactToClassMap.keySet())
                for (ArtifactEntry ae : artifactToClassMap.get(artifact))
                    writeClassArtifact(artifact, ae.className, ae.subArtifact, ae.size);
        }

        writeGenericFields(genericFields);
        artifactToClassMap.clear();
    }

    protected void writeMethodDeclaresException(String methodId, String exceptionType) {
        _db.add(METHOD_DECL_EXCEPTION, exceptionType, methodId);
    }

    protected void writeGenericFields(Iterable<GenericFieldInfo> genericFields) {
        for (GenericFieldInfo fi : genericFields) {
            if (!fi.type.contains("extends") && !fi.type.contains("super")) {
                try {
                    GenericTypeLexer lexer = new GenericTypeLexer(CharStreams.fromFileName(fi.type));
                    GenericTypeParser parser = new GenericTypeParser(new CommonTokenStream(lexer));
                    ParseTree parseTree = parser.type();
                    PrintVisitor printVisitor = new PrintVisitor(_db);
                    printVisitor.visit(parseTree);
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
            _db.add(GENERIC_FIELD, "<" + fi.definingClass + ": " + fi.type + " " + fi.name + ">", fi.definingClass, fi.name, fi.type);
        }
    }
    //_db.add(GENERIC_FIELD_TYPE, fieldInfo.definingClass, fieldInfo.name,


    protected void writePhantomType(String t) {
        _db.add(PHANTOM_TYPE, t);
    }

    protected void writePhantomMethod(String sig) {
        _db.add(PHANTOM_METHOD, sig);
    }

    protected void writeLocal(String local, String type, String method) {
        _db.add(VAR_TYPE, local, type);
        writeVarDeclaringMethod(local, method);
    }

    protected void writeVarDeclaringMethod(String local, String method) {
        _db.add(VAR_DECLARING_METHOD, local, method);
    }

    protected void writeArrayTypes(String arrayType, String componentType) {
        _db.add(ARRAY_TYPE, arrayType);
        _db.add(COMPONENT_TYPE, arrayType, componentType);
    }

    protected void writeAssignUnop(String insn, int index, String local, String methId) {
        _db.add(ASSIGN_UNOP, insn, str(index), local, methId);
    }

    protected void writeClassModifier(String c, String modifier) {
        _db.add(CLASS_MODIFIER, modifier, c);
    }

    protected void writeOperatorAt(String insn, String op) {
        _db.add(OPERATOR_AT, insn, op);
    }

    protected void writeIf(String insn, int index, int indexTo, String methodId) {
        _db.add(IF, insn, str(index), str(indexTo), methodId);
    }

    protected void writeIfConstant(String insn, String branch, String cons) {
        _db.add(IF_CONSTANT, insn, branch, cons);
    }

    protected void writeIfVar(String insn, String branch, String local) {
        _db.add(IF_VAR, insn, branch, local);
    }

    protected void writeDummyIfVar(String insn, String local) {
        _db.add(DUMMY_IF_VAR, insn, local);
    }

    protected void writeAssignBinop(String insn, int index, String local, String methodId) {
        _db.add(ASSIGN_BINOP, insn, str(index), local, methodId);
    }

    protected void writeAssignOperFrom(String insn, String branch, String local) {
        _db.add(ASSIGN_OPER_FROM, insn, branch, local);
    }

    protected void writeAssignOperFromConstant(String insn, String branch, String value) {
        _db.add(ASSIGN_OPER_FROM_CONSTANT, insn, branch, value);
    }

    protected void writeInvokedynamic(String insn, int index, String bootSig, String dynName, String dynRetType, int dynArity, String dynParamTypes, int tag, String methodId) {
        _db.add(DYNAMIC_METHOD_INV, insn, str(index), bootSig, dynName, dynRetType, str(dynArity), dynParamTypes, str(tag), methodId);
        // Make dynamic name and method type available to the analysis as string constants.
        writeStringConstant(dynName);
        writeStringConstant(dynRetType + dynParamTypes);
    }

    protected void writeInvokedynamicParameterType(String insn, int paramIndex, String type) {
        _db.add(DYNAMIC_METHOD_INV_PARAM_TYPE, insn, str(paramIndex), type);
    }

    protected void writeAssignLocal(String insn, int index, String from, String to, String methodId) {
        _db.add(ASSIGN_LOCAL, insn, str(index), from, to, methodId);
    }

    protected void writeActualParam(int index, String invo, String var) {
        _db.add(ACTUAL_PARAMETER, str(index), invo, var);
    }

    /**
     * Write a method type constant.
     *
     * @param retType     the return type of the method type
     * @param paramTypes  the parameter types of the method type
     * @param params      a String representation of the parameter
     *                    types (if null, it is reconstructed)
     */
    protected void writeMethodTypeConstant(String retType, String[] paramTypes,
                                           String params) {
        if (params == null)
            params = concatenate(paramTypes);
        String mt = "(" + params + ")" + retType;
        int arity = paramTypes.length;
        for (int idx = 0; idx < arity; idx++)
            _db.add(METHOD_TYPE_CONSTANT_PARAM, mt, str(idx), paramTypes[idx]);
        _db.add(METHOD_TYPE_CONSTANT, mt, str(arity), retType, params);
    }

    protected String concatenate(String[] elems) {
        int num = elems.length;
        if (num == 0)
            return "";
        StringBuilder sb = new StringBuilder(elems[0]);
        for (int idx = 1; idx < num; idx++) {
            sb.append(',');
            sb.append(elems[idx]);
        }
        return sb.toString();
    }

    /**
     * Write a method type constant given as a string representation.
     *
     * @param mt     the method type (such as "(java.lang.Object,int)void")
     */
    protected void writeMethodTypeConstant(String mt) {
        int rParen = mt.indexOf(")");
        if (mt.startsWith("(") && (rParen != -1)) {
            String retType = mt.substring(rParen + 1);
            // We write out the parameters part of the signature without the
            // parentheses, so that types can be added at both ends.
            String params = mt.substring(1, rParen);
            String[] paramTypes = params.split(",");
            writeMethodTypeConstant(retType, paramTypes, params);
        } else
            System.err.println("WARNING: cannot process method type " + mt);
    }

    protected void writeMethodAnnotation(String method, String annotationType) {
        _db.add(METHOD_ANNOTATION, method, annotationType);
    }

    protected void writeClassHeap(String heap, String className) {
        _db.add(CLASS_HEAP, heap, className);
        if (_extractMoreStrings)
            writeStringConstant(className);
    }

    protected void writeExceptionHandler(String insn, String method, int index,
                                         String type, int begin, int end) {
        _db.add(EXCEPTION_HANDLER, insn, method, str(index), type, str(begin), str(end));
    }

    protected void writeExceptionHandlerFormal(String insn, String var) {
        _db.add(EXCEPTION_HANDLER_FORMAL_PARAM, insn, var);
    }

    protected void writeExceptionHandlerPrevious(String currInsn, String prevInsn) {
        _db.add(EXCEPT_HANDLER_PREV, currInsn, prevInsn);
    }

    public void writeAppPackage(String appPackage) {
        _db.add(APP_PACKAGE, appPackage);
    }

    public void writePhantomTypes(Iterable<String> phantomTypes) {
        for (String s : phantomTypes) {
            System.out.println("Phantom type: " + s);
            writePhantomType(s);
        }
    }

    public void writePhantomMethods(Iterable<String> phantomMethods) {
        for (String m : phantomMethods) {
            System.out.println("Phantom method: " + m);
            writePhantomMethod(m);
        }
    }

    /**
     * Mark polymorphic methods. This currently recognizes a fixed
     * list of methods.
     *
     * @param declClass    the declaring class of the target method
     * @param simpleName   the name of the method
     *
     * @return true        if the method is polymorphic, false otherwise
     */
    public static boolean polymorphicHandling(String declClass, String simpleName) {
        return (declClass.equals("java.lang.invoke.MethodHandle") &&
                (simpleName.equals("invoke") || simpleName.equals("invokeExact") ||
                        simpleName.equals("invokeBasic")));
    }

    /**
     * Write an annotation element.
     *
     * @param annotationKind   the kind of the construct annotated by the annotation
     *                         (for example, "method" or "type")
     * @param annotatedElement the element annotated (for example, a method ID)
     * @param parentId         the ID of the parent element
     * @param thisId           the ID of this element
     * @param name             the name of this element (may be null)
     * @param value1           the value of this element
     * @param value2           an extra value (for some annotation types) or null
     */
    protected void writeAnnotationElement(String annotationKind, String annotatedElement, String parentId, String thisId, String name, String value1, String value2) {
        if (name == null)
            name = "-";
        if (value2 == null)
            value2 = "-";
        _db.add(ANNOTATION_ELEMENT, annotationKind, annotatedElement, parentId, thisId, name, value1, value2);
    }

    /**
     * Map native Java methods to native code entry points. Based on
     * section "Resolving Native Method Names" in the JNI spec:
     * https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/design.html
     *
     * Overloaded JNI names are computed later, in Datalog.
     */
    public void writeNativeMethodId(String methodId, String type, String name) {
        String jniMethodId = "Java_" + type.replaceAll("\\.", "_") + "_" + name;
        _db.add(NATIVE_METHOD_ID, methodId, jniMethodId);
    }

    protected void writeMethod(String methodId, String simpleName,
                               String paramsSig, String declType,
                               String retType, String jvmSig, String arity) {
        _db.add(METHOD, methodId, simpleName, paramsSig, declType, retType, jvmSig, arity);

        // If flag is set, register name+sig for later use (native scanner).
        if (_regMethods) {
            methodStrings.add(simpleName);
            methodStrings.add(jvmSig);
        }
    }
}
