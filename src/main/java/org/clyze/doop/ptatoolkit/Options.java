package org.clyze.doop.ptatoolkit;

/**
 * A class representing the options for the points-to analysis toolkit. This class encapsulates various configuration options that can be set by the user to customize the behavior of the points-to analysis. The options include settings such as the application to analyze, the type of analysis to perform, and whether to enable debug mode.
 */
public class Options {

	private String app;
	private String analysis = "";
	private final boolean isDebug = false;

	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public String getAnalysis() {
		return analysis;
	}

	public void setAnalysis(String analysis) {
		this.analysis = analysis;
	}

	public boolean isDebug() {
		return this.isDebug;
	}

}
