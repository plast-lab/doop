import groovy.transform.CompileStatic

@CompileStatic
class ConfigurationGenerator {
    static void main(String[] args) {
        if (args.size() == 0) {
            println "Usage: groovy ConfigurationGenerator <OUT_DIRECTORY>"
            return
        }

        String outDir = args[0]
        println "Using outDir: ${outDir}"

        computeConfiguration(outDir, 'database/_AppReachable.csv', 'database/ReachableField.csv', 'app-reachable.json')
        computeConfiguration(outDir, 'database/ApplicationMethod.csv', 'database/ApplicationField.csv', 'all.json')
    }

    static void computeConfiguration(String outDir, String methodsTable, String fieldsTable, String outFileName) {
        final Set<String> types = new HashSet<>()
        File appReachable = new File(outDir, methodsTable)
        println "Processing reachable app-methods: ${appReachable.canonicalPath}"
        final Map<String, List<Method>> rMethods = new HashMap<>()
        appReachable.withReader { BufferedReader br ->
            for (String doopId : br.readLines()) {
                Method m = Method.fromDoopId(doopId)
                // Static class initializers are not recognized by the native image builder.
                if (m.name.equals('<clinit>'))
                    continue
                types.add(m.type)
                rMethods.putIfAbsent(m.type, new LinkedList<Method>())
                rMethods.get(m.type).add(m)
            }
        }

        File reachableFields = new File(outDir, fieldsTable)
        println "Processing reachable fields: ${reachableFields.canonicalPath}"
        final Map<String, List<Field>> rFields = new HashMap<>()
        reachableFields.withReader { BufferedReader br ->
            for (String doopId : br.readLines()) {
                Field f = Field.fromDoopId(doopId)
                types.add(f.type)
                rFields.putIfAbsent(f.type, new LinkedList<Field>())
                rFields.get(f.type).add(f)
            }
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
            sb.append(tJoiner.toString())
            sb.append('\n}')
            confJoiner.add(sb.toString())
        }
        confBuilder.append(confJoiner.toString())
        confBuilder.append(']')

        println "Writing: ${outFileName}"
        new File(outFileName).withWriter { BufferedWriter bw ->
            bw.write(confBuilder.toString())
        }
    }
}

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
