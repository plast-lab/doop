package deepdoop.datalog;

interface IInitializable <T extends IInitializable> {
	T init(String id);
}
