package org.clyze.doop.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import static org.clyze.doop.common.PredicateFile.*;
import org.clyze.utils.Helper;

/**
 * This class processes the keep specification given to Doop.
 */
public class KeepSpecProcessor {
    public static void processDir(File factsDir, String file) {
        try (Database db = new Database(factsDir)) {
            processDb(db, file);
            db.flush();
        } catch (IOException ex) {
            System.err.println("Error writing entry point information: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void processDb(Database db, String file) throws IOException {
        if (file != null) {
            System.out.println("Reading keep specification from: " + file);
            try (Stream<String> stream = Files.lines(Paths.get(file))) {
                stream.forEach(s -> processFileLine(db, s));
            }
        } else
            System.err.println("WARNING: cannot read keep specification, file is null");
    }

    private static void processFileLine(Database db, String line) {
        String[] fields = line.split("\t");
        if (fields.length != 2) {
            System.err.println("WARNING: malformed line (should be two columns, tab-separated): " + line);
        }

        switch (fields[0]) {
        case "KEEP":
            db.add(KEEP_METHOD, fields[1]);
            break;
        case "KEEP_CLASS_MEMBERS":
            db.add(KEEP_CLASS_MEMBERS, fields[1]);
            break;
        case "KEEP_CLASSES_WITH_MEMBERS":
            db.add(KEEP_CLASSES_WITH_MEMBERS, fields[1]);
            break;
        default:
            System.err.println("WARNING: unsupported spec line: " + line);
        }
    }

    private static void writeKeepClass(Database db, String className) {
        db.add(KEEP_CLASS, className);
    }

}
