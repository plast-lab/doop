package org.clyze.doop.common;

import java.io.InputStream;

public interface FoundFile {
    InputStream inputStream();
    String getFilePath();
}
