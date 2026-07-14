package org.clyze.doop.jimple

import groovy.transform.CompileStatic
import org.clyze.doop.sarif.SARIFGenerator
import org.clyze.sarif.model.Result
import org.clyze.persistent.model.Element

import java.util.concurrent.atomic.AtomicInteger

import static groovy.io.FileType.FILES

/**
 * The basic logic of the Jimple processor, which can parse Jimple and
 * generate SARIF metadata that relate Jimple to existing Doop results.
 */
@CompileStatic
class JimpleProcessor extends SARIFGenerator {

    /** A directory containing Jimple code (in text form). */
    private final String jimplePath

    JimpleProcessor(String jimplePath, File db, File out, String version, boolean standalone) {
        super(db, out, version, standalone)
        this.jimplePath = jimplePath
    }

    /**
     * Main entry point.
     */
    @Override
    void process() {
        List<Result> results = new LinkedList<>()
        AtomicInteger elements = new AtomicInteger(0)
        File jimpleDir = new File(jimplePath)
        if (jimpleDir.exists() && jimpleDir.directory) {
            jimpleDir.eachFileRecurse(FILES) { JimpleListenerImpl.parseJimple(it as String, jimplePath, {
                Element e -> processElement(results, e, elements)
            })}
            println "Elements processed: ${elements}"
            generateSARIF(results)
        } else
            println "ERROR: ${jimplePath} is not a directory."
    }
}
