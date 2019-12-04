import org.clyze.analysis.Analysis;
import org.clyze.doop.Main;
import org.clyze.doop.core.DoopAnalysis;

class DoopRunner {
    public static void main(String[] args) {

        // Run an analysis.
        String[] doopArgs = new String[] {
            "-i", "http://centauri.di.uoa.gr:8081/artifactory/Demo-benchmarks/test-resources/006-hello-world-1.2.jar",
            "-a", "context-insensitive",
            "--id", "hello-world",
            "--platform", "java_8"
        };
        Main.main(doopArgs);

        // Read analysis object.
        DoopAnalysis analysis = Main.analysis;
        System.out.println("Analysis done.");
        System.out.println("* id: " + analysis.getId());
        System.out.println("* name: " + analysis.getName());
        System.out.println("* output directory: " + analysis.getOutDir());
    }
}
