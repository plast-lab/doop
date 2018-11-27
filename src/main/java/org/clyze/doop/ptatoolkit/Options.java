package org.clyze.doop.ptatoolkit;

public class Options {

	private String pta;
	private String dbPath;
	private String cachePath;
	private String app;
	private String outPath;

	private String analysis = "";
	private boolean isDebug = false;
	private String dbPath2;
	private String app2;

	public String getPTA() {
		return pta;
	}

	public void setPTA(String pta) {
		this.pta = pta;
	}

	public String getDbPath() {
		return dbPath;
	}

	public void setDbPath(String dbPath) {
		this.dbPath = dbPath;
	}

	public String getCachePath() {
		return cachePath;
	}

	public void setCachePath(String cachePath) {
		this.cachePath = cachePath;
	}

	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public String getOutPath() {
		return outPath;
	}

	public void setOutPath(String outPath) {
		this.outPath = outPath;
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
	
	public void setIsDebug(boolean isDebug) {
		this.isDebug = isDebug;
	}

	public void setDbPath2(String dbPath2) {
		this.dbPath2 = dbPath2;
	}

	public String getDbPath2() {
		return dbPath2;
	}

	public void setApp2(String app2) {
		this.app2 = app2;
	}

	public String getApp2() {
		return app2;
	}

	public static Options parse(String[] args) {
		Options opt = new Options();
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-pta")) {
				i = shift(args, i);
				opt.setPTA(args[i]);
			} else if (args[i].equals("-db")) {
				i = shift(args, i);
				opt.setDbPath(args[i]);
			} else if (args[i].equals("-cache")) {
				i = shift(args, i);
				opt.setCachePath(args[i]);
			} else if (args[i].equals("-app")) {
				i = shift(args, i);
				opt.setApp(args[i]);
			} else if (args[i].equals("-out")) {
				i = shift(args, i);
				opt.setOutPath(args[i]);
			} else if (args[i].equals("-a")) {
				i = shift(args, i);
				opt.setAnalysis(args[i]);
			} else if (args[i].equals("-db2")) {
				i = shift(args, i);
				opt.setDbPath2(args[i]);
			} else if (args[i].equals("-app2")) {
				i = shift(args, i);
				opt.setApp2(args[i]);
			} else if (args[i].equals("-debug")) {
				opt.setIsDebug(true);
				Global.setDebug(true);
			} else if (args[i].equals("-flow")) {
				i = shift(args, i);
				Global.setFlow(args[i]);
				switch (args[i]) {
					case "Direct": {
						Global.setEnableWrappedFlow(false);
						Global.setEnableUnwrappedFlow(false);
					}
					break;
					case "Direct+Wrapped": {
						Global.setEnableUnwrappedFlow(false);
					}
					break;
					case "Direct+Unwrapped": {
						Global.setEnableWrappedFlow(false);
					}
					break;
					case "Direct+Wrapped+Unwrapped": {
						Global.setEnableWrappedFlow(true);
						Global.setEnableUnwrappedFlow(true);
					}
					break;
					default: {
						throw new Error("Unknown -flow argument: " + args[i]);
					}
				}
			} else if (args[i].equals("-no-wrapped-flow")) {
				Global.setEnableWrappedFlow(false);
			} else if (args[i].equals("-no-unwrapped-flow")) {
				Global.setEnableUnwrappedFlow(false);
			} else if (args[i].equals("-express")) {
				Global.setExpress(true);
			} else if (args[i].equals("-thread")) {
				i = shift(args, i);
				Global.setThread(Integer.parseInt(args[i]));
			} else if (args[i].equals("-tst")) {
				i = shift(args, i);
				Global.setTST(Integer.parseInt(args[i]));
			} else if (args[i].equals("-list-context")) {
				Global.setListContext(true);
			} else {
				throw new RuntimeException("Unexpected options: " + args[i]);
			}
		}
		return opt;
	}
	
	private static int shift(String[] args, int index) {
		if (args.length == index + 1) {
			System.err.println("error: option " + args[index]
					+ " requires an argument");
			System.exit(1);
		}

		return index + 1;
	}
}
