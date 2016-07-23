package deepdoop;

import deepdoop.actions.*;
import deepdoop.datalog.DatalogLexer;
import deepdoop.datalog.DatalogListenerImpl;
import deepdoop.datalog.DatalogParser;
import deepdoop.datalog.Program;
import deepdoop.datalog.component.Component;
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

			PostOrderVisitor<IVisitable> v = new PostOrderVisitor<>(new FlatteningActor(p.comps));
			Program flatP = (Program) p.accept(v);

			flatP.accept(new LBCodeGenVisitingActor("build/"));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
