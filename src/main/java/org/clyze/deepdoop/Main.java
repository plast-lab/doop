package org.clyze.deepdoop;

import java.io.IOException;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.DatalogLexer;
import org.clyze.deepdoop.datalog.DatalogListenerImpl;
import org.clyze.deepdoop.datalog.DatalogParser;
import org.clyze.deepdoop.datalog.Program;
import org.clyze.deepdoop.datalog.component.Component;

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
