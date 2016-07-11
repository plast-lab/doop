package deepdoop.datalog.component;

import java.util.Set;

public class Propagation {

	public final String      fromId;
	public final Set<String> preds;
	public final String      toId;

	public Propagation(String fromId, Set<String> preds, String toId) {
		this.fromId = fromId;
		this.preds  = preds;
		this.toId   = toId;
	}
}
