package doop.preprocess

import org.anarres.cpp.JavaFileSystem
import org.anarres.cpp.VirtualFile
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 19/7/2014
 *
 * Used for debugging the JcppPreprocessor (may be removed)
 */
class FileSystem extends JavaFileSystem {
    Log logger = LogFactory.getLog(getClass())

    @Override
    VirtualFile getFile(String path) {
        logger.debug("Getting file:$path")
        return super.getFile(path)
    }

    @Override
    VirtualFile getFile(String dir, String name) {
        logger.debug("Getting file:$dir, $name")
        return super.getFile(dir, name)
    }
}
