package deepdoop.datalog;

import java.io.IOException;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class Main {

	public static void main(String[] args) {
		try {
			DatalogParser parser = new DatalogParser(
					new CommonTokenStream(
						new DatalogLexer(
							new ANTLRFileStream(args[0]))));

			DatalogListenerImpl listener = new DatalogListenerImpl();

			ParseTreeWalker.DEFAULT.walk(listener, parser.program());

			for (Predicate p : listener._predicates)
				System.out.println(p);
			for (Predicate p : listener._specialPredicates)
				System.out.println(p);
			System.out.println("=======");
			for (Rule r : listener._rules)
				System.out.println(r);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
