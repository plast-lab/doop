package org.clyze.deepdoop;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.*;
import org.clyze.deepdoop.system.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class DatalogTest {

	DatalogListenerImpl _listener;
	ParseTreeWalker     _walker;

	DatalogParser open(String filename) throws IOException {
		return new DatalogParser(
				new CommonTokenStream(
					new DatalogLexer(
						new ANTLRInputStream(
							getClass().getResourceAsStream(filename)))));
	}

	public void test(String filename) throws IOException {
		test(filename, null);
	}
	public void test(String filename, ErrorId expectedErrorId) throws IOException {
		filename = "/deepdoop/" + filename;
		try {
			ParseTree tree = open(filename).program();
			_walker.walk(_listener, tree);

			Program p = _listener.getProgram();

			PostOrderVisitor<IVisitable> v = new PostOrderVisitor<>(new FlatteningActor(p.comps));
			Program flatP = (Program) p.accept(v);

			flatP.accept(new LBCodeGenVisitingActor("build/"));
		}
		catch (DeepDoopException e) {
			if (expectedErrorId == null || e.errorId != expectedErrorId)
				Assert.fail(e.errorId + e.getMessage() + " on " + filename);
			System.err.println("Expected failure on " + filename);
		}
		catch (Exception e) {
			Assert.fail(e.getMessage() + " on " + filename);
		}
	}

	// This method is run before each method annotated with @Test
	@Before
	public void setup() throws IOException {
		_listener = new DatalogListenerImpl();
		_walker   = new ParseTreeWalker();
	}

	@Test
	public void testT1() throws IOException {
		test("t1.logic");
	}
	@Test
	public void testT2() throws IOException {
		test("t2.logic");
	}
	@Test
	public void testT3() throws IOException {
		test("t3.logic");
	}
	@Test
	public void testT4() throws IOException {
		test("t4.logic");
	}
	@Test
	public void testT5() throws IOException {
		test("t5.logic");
	}
	@Test
	public void testT6() throws IOException {
		test("t6.logic");
	}
	@Test
	public void testT7() throws IOException {
		test("t7.logic");
	}
	@Test
	public void testT8() throws IOException {
		test("t8.logic");
	}
	@Test
	public void testT9() throws IOException {
		test("t9.logic");
	}
	@Test
	public void testSample() throws IOException {
		test("sample.logic");
	}

	@Test
	public void test_Fail1() throws IOException {
		test("fail1.logic", ErrorId.DEP_CYCLE);
	}
	@Test
	public void test_Fail2() throws IOException {
		test("fail2.logic", ErrorId.DEP_GLOBAL);
	}
	@Test
	public void test_Fail5() throws IOException {
		test("fail5.logic", ErrorId.CMD_RULE);
	}
	@Test
	public void test_Fail6() throws IOException {
		test("fail6.logic", ErrorId.CMD_CONSTRAINT);
	}
	@Test
	public void test_Fail7() throws IOException {
		test("fail7.logic", ErrorId.CMD_DIRECTIVE);
	}
	@Test
	public void test_Fail8() throws IOException {
		test("fail8.logic", ErrorId.CMD_NO_DECL);
	}
	@Test
	public void test_Fail9() throws IOException {
		test("fail9.logic", ErrorId.CMD_NO_IMPORT);
	}
	@Test
	public void test_Fail10() throws IOException {
		test("fail10.logic", ErrorId.CMD_EVAL);
	}
	@Test
	public void test_Fail11() throws IOException {
		test("fail11.logic", ErrorId.ID_IN_USE);
	}
	@Test
	public void test_Fail12() throws IOException {
		test("fail12.logic", ErrorId.UNKNOWN_VAR);
	}
	@Test
	public void test_Fail13() throws IOException {
		test("fail13.logic", ErrorId.UNKNOWN_VAR);
	}


	@Test
	public void testA01() throws IOException {
		test("analysis/cfg-tests.logic");
	}
	@Test
	public void testA02() throws IOException {
		test("analysis/context-insensitive-declarations.logic");
	}
	@Test
	public void testA03() throws IOException {
		test("analysis/context-insensitive.logic");
	}
	@Test
	public void testA04() throws IOException {
		test("analysis/facts-declarations.logic");
	}
	@Test
	public void testA05() throws IOException {
		test("analysis/flow-insensitivity-declarations.logic");
	}
	@Test
	public void testA06() throws IOException {
		test("analysis/statistics-simple.logic");
	}
	@Test
	public void testA07() throws IOException {
		test("analysis/tamiflex-declarations.logic");
	}
	@Test
	public void testA08() throws IOException {
		test("analysis/tamiflex-fact-declarations.logic");
	}
	@Test
	public void testA09() throws IOException {
		test("analysis/tamiflex.logic");
	}
}
