package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;

public class Names {
	public static String nameId(String name, String id) {
		return (id != null ? id + ":" : "") + name;
	}

	public static String nameStage(String name, String stage) {
		if (stage == null) return name;
		else               return name + (stage.equals("@past") ? ":past" : stage);
	}

	public static List<IExpr> newVars(int count) {
		List<IExpr> vars = new ArrayList<>(count);
		for (int i = 0 ; i < count ; i++) vars.add(new VariableExpr("var" + i));
		return vars;
	}
}
