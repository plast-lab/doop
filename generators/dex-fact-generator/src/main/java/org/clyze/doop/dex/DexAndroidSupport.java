package org.clyze.doop.dex;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Parameters;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.common.android.AppResources;

class DexAndroidSupport extends AndroidSupport {

    private final Log logger;

    DexAndroidSupport(Parameters parameters, BasicJavaSupport java) {
        super(parameters, java);
        this.logger = LogFactory.getLog(getClass());
    }

    @Override
    public AppResources processAppResources(String archiveLocation) throws Exception {
        logger.debug("Processing Dex app resources in: " + archiveLocation);
        if (archiveLocation.toLowerCase().endsWith(".apk"))
            return super.processAppResources(archiveLocation);
        else
            throw new RuntimeException("Archive type not supported by front-end: " + archiveLocation);
    }
}
