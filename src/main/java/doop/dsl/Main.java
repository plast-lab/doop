package doop.dsl;

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

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
