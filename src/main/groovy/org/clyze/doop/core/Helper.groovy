package org.clyze.doop.core

class Helper {
    /**
     * Returns a list of the names of the available analyses in the given doop analyses directory
     */
    static List<String> namesOfAvailableAnalyses(String doopAnalysesDir) {
        List<String> analyses = []
        new File(doopAnalysesDir).eachDir { File dir ->
            if (dir.getName().indexOf("sensitive") != -1 ) {
                File f = new File(dir, "analysis.logic")
                if (f.exists() && f.isFile()) {
                    analyses.push(dir.getName())
                }
            }
        }
        return analyses
    }

    static void checkMandatoryArgs(OptionAccessor cli) {
        boolean noAnalysis = !cli.a, noJar = !cli.i
        boolean error = noAnalysis || noJar

        if (error)
            throw new RuntimeException("Missing required argument(s): " + (noAnalysis ? "a" : "") +
                    (noJar ? (noAnalysis ? ", " : "") + "i" : ""))
    }
}
