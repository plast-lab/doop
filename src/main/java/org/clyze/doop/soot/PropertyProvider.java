package org.clyze.doop.soot;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static soot.SourceLocator.FoundFile;

class PropertyProvider {
    private Map<String, Properties> _properties;

    public PropertyProvider() {
        _properties = new HashMap<>();
    }

    /**
     * Adds a properties file from a resource.
     */
    public void addProperties(FoundFile foundFile)
        throws IOException
    {
        Properties properties = new Properties();
        InputStream stream = foundFile.inputStream();

        try {
            properties.load(stream);
        }
        catch (IOException exc) {
            properties.clear();
            properties.loadFromXML(stream);
        }
        finally {
            if(stream != null)
                stream.close();
        }

        _properties.put(foundFile.getFilePath(), properties);
    }

    public Map<String, Properties> getProperties() {
        return _properties;
    }
}
