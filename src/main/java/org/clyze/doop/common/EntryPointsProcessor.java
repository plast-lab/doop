package org.clyze.doop.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import static org.clyze.doop.common.PredicateFile.*;
import org.clyze.utils.Helper;

/**
 * This class processes entry points given to Doop (supporting
 * ProGuard seeds syntax).
 */
public class EntryPointsProcessor {
    public void processDir(File factsDir, String file) {
        try (Database db = new Database(factsDir)) {
            processDb(db, file);
            db.flush();
        } catch (IOException ex) {
            System.err.println("Error writing entry point information: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void processDb(Database db, String file) throws IOException {
        if (file != null) {
            System.out.println("Reading entry points from: " + file);
            try (Stream<String> stream = Files.lines(Paths.get(file))) {
                stream.forEach(s -> processFileLine(db, s));
            }
        }
    }

    private void processFileLine(Database db, String line) {
        // The entry points file may contain method doopIds or proguard seeds.
        if (line.startsWith("<"))
            writeAndroidKeepMethodDoopId(db, line);
        else if (line.contains("(")) {
            // The proguard seeds file notation does not use doopIds for constructors.
            // e.g. in a seeds file we have:
            //   package.class$innerClass: class$innerClass(args...)
            // instead of:
            //   package.class$innerClass: void <init>(args...)
            String doopId = Helper.readMethodDoopId(line);
            writeAndroidKeepMethodDoopId(db, doopId);
        } else if (!line.contains(":"))
            writeAndroidKeepClass(db, line);
    }

    protected void writeAndroidKeepMethodDoopId(Database db, String doopId) {
        db.add(ANDROID_KEEP_METHOD, doopId);
    }

    private void writeAndroidKeepClass(Database db, String className) {
        db.add(ANDROID_KEEP_CLASS, className);
    }

}
