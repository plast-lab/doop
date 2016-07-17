package deepdoop;

import deepdoop.actions.*;
import deepdoop.datalog.DatalogLexer;
import deepdoop.datalog.DatalogListenerImpl;
import deepdoop.datalog.DatalogParser;
import deepdoop.datalog.Program;
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

			Program p = listener.getProgram();

			PostOrderVisitor<IVisitable> v;
			v = new PostOrderVisitor<>(new FlatteningActor(p.comps));
			Program flatP = (Program) p.accept(v);
			v = new InitVisitingActor();
			Program initP = (Program) flatP.accept(v);
			System.out.println(initP);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
