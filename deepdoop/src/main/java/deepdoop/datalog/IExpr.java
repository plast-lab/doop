package deepdoop.datalog;

interface IExpr {
	default IExpr init(String id) {
		return this;
	}
}
