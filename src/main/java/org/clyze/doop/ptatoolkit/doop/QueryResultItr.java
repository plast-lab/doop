package org.clyze.doop.ptatoolkit.doop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator on the result file of given query. Each "next" element
 * corresponds to a line of the result file.
 */
class QueryResultItr implements Iterator<List<String>> {

    private static final String SEP = "\t";

    private Query query;
    private BufferedReader reader;
    private String nextLine;
    
    QueryResultItr(Query query, File resultFile) {
        this.query = query;
        try {
            reader = new BufferedReader(new FileReader(resultFile));
            nextLine = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Query " + query + " fails, " +
                    "caused by " + e.getMessage());
        }
    }

    @Override
    public boolean hasNext() {
        return nextLine != null;
    }

    @Override
    public List<String> next() {
        if (hasNext()) {
            String line = nextLine;
            try {
                nextLine = reader.readLine();
            } catch (IOException e) {
                throw new RuntimeException("Query " + query + " fails, " +
                        "caused by " + e.getMessage());
            }
            return line2list(line);
        } else {
            throw new NoSuchElementException(query.name());
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if(reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            reader = null;
        } finally {
            super.finalize();
        }
    }

    private List<String> line2list(String line) {
        return Arrays.asList(line.trim().split(SEP));
    }
}
