package org.clyze.doop.utils

import groovy.transform.CompileStatic

/**
 * A configuration generator for use with GraalVM and the native image builder.
 */
@CompileStatic
class ConfigurationGenerator {
    final String analysisOutDir
    final String confOutDir

    static void main(String[] args) {
        if (args.size() == 0) {
            println "Usage: groovy ConfigurationGenerator <OUT_DIRECTORY>"
            return
        }

        new ConfigurationGenerator(args[0], '.').generateConfigurations()
    }

    /**
     * Main constructor.
     *
     * @param analysisOutDir    the Doop analysis output directory (parent of database/ directory)
     * @param confOutDir        the output directory where configurations will be written
     */
    ConfigurationGenerator(String analysisOutDir, String confOutDir) {
        this.analysisOutDir = analysisOutDir
        this.confOutDir = confOutDir
        println "Using analysis output directory: ${analysisOutDir}"
    }

    /**
     * Generates the configuration files from analysis outputs.
     */
    void generateConfigurations() {
        // app-reachable, flat configuration
        computeConfiguration('mainAnalysis.AppReachable.csv', 'ReachableField.csv', 'app-reachable-flat.json')
        // reflective uses only, flat configuration
        computeConfiguration('MethodInvokedByReflection.csv', 'ReachableFieldAccessedByReflection.csv', 'app-reachable-refl.json')
        // native call-backs only, flat configuration
        computeConfiguration('AppReachableMethodFromNativeCode.csv', null, 'app-reachable-native.json')
        // pattern-based configurations (experimental)
        computeConfiguration('NI_ReachableMethod.csv', 'NI_ReachableField.csv', 'app-reachable.json', true)
        // whole-program dummy configuration (for benchmarking)
        computeConfiguration('ApplicationMethod.csv', 'ApplicationField.csv', 'all.json')
    }

    private void computeConfiguration(String methodsTable, String fieldsTable, String outFileName, boolean usePatterns = false) {
        final SortedSet<String> types = new TreeSet<>()
        final Map<String, List<Method>> rMethods = new HashMap<>()
        final Map<String, List<Field>> rFields = new HashMap<>()

        if (methodsTable == null)
            println "WARNING: Method information is not available."
        else {
            File appReachable = new File(analysisOutDir, 'database/' + methodsTable)
            if (appReachable.exists()) {
                println "| Processing reachable app-methods: ${appReachable.canonicalPath}"
                appReachable.withReader { BufferedReader br ->
                    for (String doopId : br.readLines()) {
                        if (doopId.size() == 0)
                            continue
                        try {
                            Method m = Method.fromDoopId(doopId)
                            // Static class initializers are not recognized by the native image builder.
                            if (m.name.equals('<clinit>'))
                                continue
                            types.add(m.type)
                            rMethods.putIfAbsent(m.type, new LinkedList<Method>())
                            rMethods.get(m.type).add(m)
                        } catch (Exception ex) {
                            System.err.println("ERROR: Could not process line: '${doopId}'")
                            ex.printStackTrace()
                        }
                    }
                }
            } else
                println("WARNING: File does not exist: " + appReachable.canonicalPath)
        }

        if (fieldsTable == null)
            println "WARNING: Field information is not available."
        else {
            File reachableFields = new File(analysisOutDir, 'database/' + fieldsTable)
            if (reachableFields.exists()) {
                println "| Processing reachable fields: ${reachableFields.canonicalPath}"
                reachableFields.withReader { BufferedReader br ->
                    for (String doopId : br.readLines()) {
                        Field f = Field.fromDoopId(doopId)
                        types.add(f.type)
                        rFields.putIfAbsent(f.type, new LinkedList<Field>())
                        rFields.get(f.type).add(f)
                    }
                }
            } else
                println("WARNING: File does not exist: " + reachableFields.canonicalPath)
        }

        SortedSet<String> allDeclaredConstructors_Types = new TreeSet<>()
        SortedSet<String> allPublicConstructors_Types   = new TreeSet<>()
        SortedSet<String> allDeclaredMethods_Types      = new TreeSet<>()
        SortedSet<String> allPublicMethods_Types        = new TreeSet<>()
        SortedSet<String> allDeclaredFields_Types       = new TreeSet<>()
        SortedSet<String> allPublicFields_Types         = new TreeSet<>()
        if (usePatterns) {
            allDeclaredConstructors_Types = readTypesFrom(types, 'database/NI_AllDeclaredConstructors.csv')
            allPublicConstructors_Types   = readTypesFrom(types, 'database/NI_AllPublicConstructors.csv')
            allDeclaredMethods_Types      = readTypesFrom(types, 'database/NI_AllDeclaredMethods.csv')
            allPublicMethods_Types        = readTypesFrom(types, 'database/NI_AllPublicMethods.csv')
            allDeclaredFields_Types       = readTypesFrom(types, 'database/NI_AllDeclaredFields.csv')
            allPublicFields_Types         = readTypesFrom(types, '/database/NI_AllPublicFields.csv')
        }

        StringBuilder confBuilder = new StringBuilder('[\n')
        StringJoiner confJoiner = new StringJoiner(',\n')
        for (String type : types) {
            StringBuilder sb = new StringBuilder()
            sb.append('{\n')
            final String TAB1 = '  '
            final String TAB2 = TAB1 + TAB1
            StringJoiner tJoiner = new StringJoiner(',\n')
            tJoiner.add(TAB1 + "\"name\": \"${type}\"")
            List<Method> methods = rMethods.get(type)
            if (methods) {
                tJoiner.add(TAB1 + '\"methods\": [\n' + new MemberList(TAB2, methods).toString() + '\n' + TAB1 + ']')
            }
            List<Field> fields = rFields.get(type)
            if (fields) {
                tJoiner.add(TAB1 + '\"fields\": [\n' + new MemberList(TAB2, fields).toString() + '\n' + TAB1 + ']')
            }
            if (usePatterns) {
                if (allDeclaredConstructors_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allDeclaredConstructors\" : true")
                if (allPublicConstructors_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allPublicConstructors\" : true")
                if (allDeclaredMethods_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allDeclaredMethods\" : true")
                if (allPublicMethods_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allPublicMethods\" : true")
                if (allDeclaredFields_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allDeclaredFields\" : true")
                if (allPublicFields_Types.contains(type))
                    tJoiner.add(TAB1 + "\"allPublicFields\" : true")
            }
            sb.append(tJoiner.toString())
            sb.append('\n}')
            confJoiner.add(sb.toString())
        }
        confBuilder.append(confJoiner.toString())
        confBuilder.append(']')

        File outFile = new File(confOutDir, outFileName)
        println "\\--> Writing: ${outFile.canonicalPath}"
        outFile.withWriter { BufferedWriter bw ->
            bw.write(confBuilder.toString())
        }
    }

    private SortedSet<String> readTypesFrom(Set<String> types, String path) {
        File f = new File(analysisOutDir, path)
        println "| Processing: ${f.canonicalPath}"
        Set<String> rTypes = new TreeSet<>()
        f.withReader { BufferedReader br ->
            br.readLines().each { String typeId ->
                rTypes.add(typeId)
                types.add(typeId)
            }
        }
        return rTypes
    }
}

/**
 * Helper class for JSON output (list of methods/fields).
 */
class MemberList {
    final String TAB
    final List<? extends Member> members
    MemberList(String TAB, List<? extends Member> members) {
        this.TAB = TAB
        this.members = members
    }

    @Override
    String toString() {
        StringJoiner mJoiner = new StringJoiner(',\n')
        for (Member m : members)
            mJoiner.add(TAB + m.toString())
        return mJoiner.toString()
    }
}

/**
 * Helper class for JSON output (method or field).
 */
class Member {
    final String type
    final String name

    protected Member(String type, String name) {
        this.type = type
        this.name = name
    }

    protected static unquote(String line) {
        return line.substring(line.indexOf('<') + 1, line.lastIndexOf('>'))
    }

    protected static getTypePrefix(String text) {
        return text.substring(0, text.indexOf(':'))
    }
}

/**
 * Helper class for JSON output (method).
 */
class Method extends Member {
    final List<String> paramTypes = new LinkedList<>()

    private Method(String type, String name, String signature) {
        super(type, name)
        if (signature != null)
            for (String paramType : signature.split(','))
                paramTypes.add(paramType)
    }

    @Override
    String toString() {
        StringJoiner joiner = new StringJoiner(',')
        for (String paramType : paramTypes)
            joiner.add("\"${paramType}\"")
        return "{ \"name\": \"${name}\", \"parameterTypes\": [${joiner.toString()}] }"
    }

    static Method fromDoopId(String doopId) {
        String withoutQuotes = unquote(doopId)
        String type = getTypePrefix(withoutQuotes)
        String signatureSuffix = withoutQuotes.substring(withoutQuotes.indexOf(':') + 1)
        String name = signatureSuffix.substring(signatureSuffix.indexOf(' ', 2) + 1, signatureSuffix.indexOf('('))
        String signature = signatureSuffix.contains('()') ? null : signatureSuffix.substring(signatureSuffix.indexOf('(') + 1, signatureSuffix.indexOf(')'))
        return new Method(type, name, signature)
    }
}

/**
 * Helper class for JSON output (field).
 */
class Field extends Member {
    private Field(String type, String name) {
        super(type, name)
    }

    @Override
    String toString() {
        return "{ \"name\": \"${name}\" }"
    }

    static Field fromDoopId(String doopId) {
        String withoutQuotes = Member.unquote(doopId)
        String type = Member.getTypePrefix(withoutQuotes)
        String signatureSuffix = withoutQuotes.substring(withoutQuotes.indexOf(':') + 1)
        String name = signatureSuffix.substring(signatureSuffix.indexOf(' ', 2) + 1)
        return new Field(type, name)
    }
}
