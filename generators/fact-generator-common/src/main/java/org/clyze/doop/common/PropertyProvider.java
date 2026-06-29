package org.clyze.doop.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropertyProvider {
    private final Map<String, Properties> _properties;

    public PropertyProvider() {
        _properties = new HashMap<>();
    }

    /**
     * Adds a properties file from a properties entry inside an archive.
     *
     * @param stream     the InputStream of the properties entry
     * @param filePath   the path of the archive containing the
     *                   properties file
     */
    public void addProperties(InputStream stream, String filePath) throws IOException {
        Properties properties = new Properties();

        try {
            properties.load(stream);
        }
        catch (IOException exc) {
            properties.clear();
            properties.loadFromXML(stream);
        }
        catch (IllegalArgumentException exc) {
            // A malformed properties file (e.g. a bad \\uXXXX escape, as shipped
            // in some BouncyCastle jars) makes Properties.load throw an unchecked
            // IllegalArgumentException. Skip the bad file instead of aborting the
            // whole fact-generation run (preprocessing is not covered by
            // --ignore-factgen-errors).
            System.err.println("WARNING: skipping malformed properties file in " + filePath + ": " + exc.getMessage());
            properties.clear();
        }
        finally {
            if(stream != null)
                stream.close();
        }

        _properties.put(filePath, properties);
    }

    public Map<String, Properties> getProperties() {
        return _properties;
    }
}
