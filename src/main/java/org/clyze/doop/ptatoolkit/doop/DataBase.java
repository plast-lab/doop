package org.clyze.doop.ptatoolkit.doop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * The DataBase class provides an interface to query the results of a Doop analysis. It uses a logger for logging purposes and manages the directory where the query results are stored. The query method allows users to retrieve the results of a given query as an iterator over lists of strings, which represent the rows of the result set. The getResultFile method constructs the file path for the result file based on the query name, and the getResultFilePath method formats the file path using the database directory and the query name.
 * 
 */
public class DataBase {

	protected Log logger;
	private final File dbDir;

	/**
	 * Constructor for DataBase that initializes the logger and the database directory.
	 * @param dbDir the directory where the query results are stored
	 */
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
