package org.clyze.doop.ptatoolkit.doop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * A class that represents the database of Doop.
 *
 */
public class DataBase {

	protected Log logger;
	private final File dbDir;

	public DataBase(File dbDir) {
		this.logger = LogFactory.getLog(getClass());
		this.dbDir = dbDir;
	}

	/** Return the results of the give query. */
	public Iterator<List<String>> query(Query query) {
		File resultFile = getResultFile(query);
		return new QueryResultItr(query, resultFile);
	}

	/**
	 * Get the result file according to given query.
	 * @param query
	 * @return
	 */
	private File getResultFile(Query query) {
		String queryName = query.name();
		return new File(getResultFilePath(queryName));
	}
	
	private String getResultFilePath(String queryName) {
		String fileName = String.format("%s.csv", queryName);
		return String.format("%s%s%s", dbDir.getAbsolutePath(), File.separator, fileName);
	}
}
