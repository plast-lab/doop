// Reads a context-insensitive logic file and removes all context fields
// from its relations. This code is a search & replace heuristic.

package org.clyze.doop.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption

public class ContextRemover {
    // Relations containing context fields (but also other fields).
    static HashMap<String, List<Integer>> fieldsToRemove = new HashMap<>();
    // Relations that should be removed, such as those containing only context fields.
    static HashSet<String> relationsToDelete = new HashSet<>();
    static HashSet<String> extraRelationsToDelete =
        [ "ContextResponse",
          "StaticContextResponse",
          "RecordContextResponse",
          "ThreadStartContextResponse",
          "StartupContextResponse",
          "FinalizerRegisterContextResponse",
          "InitContextResponse",
          "InitHContextResponse" ] as Set
    // Line counter.
    static int lineN = 0
    // Trivially true fact to use when replacing context relations.
    static final String CONST_TRUE = "TRUE(1)"
    // Text to delete explicitly.
    static final List textToDelete = [
        "?callerCtx = ?callerCtx",
        "?hctx = ?hctx",
        "?groupHCtx = ?groupHCtx",
        "?calleeCtx = ?callerCtx",
        "?ctx = ?ctx",
        "?hctx = \"<<unique-hcontext>>\"",
        "?newCtx = \"<<unique-context>>\"",
        "?calleeCtx = \"<<unique-context>>\"",
        "?ctx = \"<<unique-context>>\"",
        "?hctx = \"<<unique-hcontext>>\""
        ]

    static void removeContexts(File inFile, File outFile) {
        if (inFile.canonicalPath.indexOf("context-insensitive") == -1) {
            throw new RuntimeException("The context-removal preprocessor only supports context-insensitive logic.")
        }

        println "WARNING: Running the experimental context remover..."
        outFile.delete()

        List outLines = ['.decl TRUE(t:number)', "${CONST_TRUE}."]
        // First pass: recognize and transform declarations.
        inFile.eachLine { String line -> outLines << translateDecl(line) }
        // Second pass: transform logic and remove most trivial placeholders.
        outLines = outLines.collect { translateLine(it) }
                           .collect { it.replace("${CONST_TRUE},", "") }
        // Write output.
        outFile << outLines.join('\n')
    }

    static String translateDecl(String line) {
        List lineSubstitutions = []
        int declIdx = line.indexOf(".decl ")
        if (declIdx != -1) {
            int lParenIdx = line.indexOf("(")
            int rParenIdx = line.indexOf(")")
            if ((lParenIdx == -1) || (rParenIdx == -1)) {
                throw new RuntimeException(".decl without '('/')'!")
            }
            String relName = line.substring(declIdx + ".decl ".size(), lParenIdx)
            List fields = line.substring(lParenIdx + 1, rParenIdx).split(',')
            List newFields = []
            fields.eachWithIndex { String fld, int idx ->
                if (isContextField(fld.trim())) {
                    registerFieldToRemove(relName, idx)
                } else {
                    newFields << fld
                }
            }
            // If no fields left, this is a relation involving only contexts.
            if ((newFields.size() == 0) || extraRelationsToDelete.contains(relName)) {
                fieldsToRemove.remove(relName)
                relationsToDelete.add(relName)
                // println "Marking context relation ${relName}."
            } else {
                String original = line.substring(declIdx, rParenIdx + 1)
                String decl = ".decl " + relName + "(" + newFields.join(',') + ")"
                lineSubstitutions << [original, decl]
            }
            return subst(lineSubstitutions, line)
        }
        return line
    }

    static String translateLine(String line) {
        List lineSubstitutions = []
        lineN = lineN + 1

        // Already processed.
        if (line.indexOf(".decl ") != -1) { return line }

        // Text that should be deleted.
        textToDelete.each { lineSubstitutions << [it, CONST_TRUE] }

        // Compute substitutions for relations containing contexts.
        for (String relName in fieldsToRemove.keySet()) {
            findRels(line, relName).each { List r ->
                if (r[0] != -1) {
                    String relHead = r[2]
                    String original = r[4]
                    Set indices = fieldsToRemove.get(relName) as Set
                    List args = r[5]
                    List newArgs = []
                    args.eachWithIndex { elem, idx ->
                        if (!indices.contains(idx)) { newArgs << elem }
                    }
                    String res = "${relHead}" + newArgs.join(",") + ")"
                    lineSubstitutions << [original, res]
                }
            }
        }
        // Replace context-only relations with trivial truths.
        relationsToDelete.each { String relName ->
            findRels(line, relName).each { List r ->
                if (r[0] != -1) { lineSubstitutions << [r[4], CONST_TRUE] }
            }
        }
        return subst(lineSubstitutions, line)
    }

    static String subst(List lineSubstitutions, String line) {
        lineSubstitutions.forEach { List subst ->
            line = line.replace(subst[0], subst[1])
            // println "${lineN}: [${subst[0]}] ~> [${subst[1]}]"
        }
        return line
    }

    // Split a string at commas, ignoring substrings in parentheses.
    static List splitOuter(String s) {
        List l = []
        String current = ""
        int parens = 0
        for (char c in s.toCharArray()) {
            if ((c == ',') && (parens == 0)) {
                l << current
                current = ""
            } else {
                if (c=='(') { parens += 1 }
                if (c==')') { parens -= 1 }
                current += c
            }
        }
        if (current.size() > 0) { l << current }
        return l
    }

    // Find next ')' that doesn't match a prepending '('.
    static int matchingRParen(String s, int startIdx) {
        int parens = 1
        def cs = s.toCharArray();
        for (int idx = startIdx; idx < s.size(); idx++) {
            if (cs[idx] == '(') {
                parens += 1
            } else if (cs[idx] == ')') {
                parens -= 1
                if (parens == 0) { return idx }
            }
        }
        return (-1)
    }

    static List findRels(String line, String relName) {
        List ret = []
        int startIdx = 0
        while (true) {
            List r = findRel(line, relName, startIdx)
            ret << r
            startIdx = r[3]
            if (startIdx == -1) {
                return ret
            }
        }
    }

    // Pattern matching for relation uses.
    static List findRel(String line, String relName, int startIdx) {
        String relHead = "${relName}("
        int relHeadStartIdx
        int relHeadEndIdx = -1
        int rParenIdx = -1
        String original = null
        List args = []
        // Find relation at start of line, middle of line (prepended
        // by space), or used in a component (dot notation).
        if ((startIdx == 0) && (line.startsWith(relHead))) {
            relHeadStartIdx = 0
        } else {
            relHead = " ${relName}("
            relHeadStartIdx = line.indexOf(relHead, startIdx)
            if (relHeadStartIdx == -1) {
                relHead = ".${relName}("
                relHeadStartIdx = line.indexOf(relHead, startIdx)
                if (relHeadStartIdx != -1) {
                    int componentStartIdx = findWordStartBackwards(line, relHeadStartIdx - 1)
                    relHead = line.substring(componentStartIdx, relHeadStartIdx) + relHead
                    relHeadStartIdx = componentStartIdx
                }
            }
        }
        if (relHeadStartIdx != -1) {
            relHeadEndIdx = relHeadStartIdx + relHead.size()
            rParenIdx = matchingRParen(line, relHeadEndIdx)
            if (rParenIdx == -1) {
                throw new RuntimeException("no ')' in: ${line}")
            }
            original = line.substring(relHeadStartIdx, rParenIdx + 1)
            args = splitOuter(line.substring(relHeadEndIdx, rParenIdx))
        }
        return [relHeadStartIdx, relHeadEndIdx, relHead, rParenIdx, original, args]
    }

    static int findWordStartBackwards(String line, int startIdx) {
        for (int idx = startIdx; idx >= 0; idx--) {
            if (!((Character)line[idx]).isLetter())
                return idx
        }
        return 0
    }

    static boolean isContextField(String s) {
        String t = (s.split(":"))[1].trim()
        List contextTypes = [ "Context", "HContext", "configuration.Context", "configuration.HContext", "mainAnalysis.configuration.Context", "mainAnalysis.configuration.HContext" ]
        return contextTypes.contains(t)
    }

    static void registerFieldToRemove(String relName, int idx) {
        List l = fieldsToRemove.get(relName)
        if (l == null) {
            l = [ idx ]
        } else { 
            l << idx
        }
        fieldsToRemove.put(relName, l)
    }
}
