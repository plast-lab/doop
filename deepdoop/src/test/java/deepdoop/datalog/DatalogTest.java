package deepdoop.datalog;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class DatalogTest {

	DatalogListener _listener;
	ParseTreeWalker _walker;

	DatalogParser open(String filename) throws IOException {
		return new DatalogParser(
				new CommonTokenStream(
					new DatalogLexer(
						new ANTLRInputStream(
							getClass().getResourceAsStream(filename)))));
	}

	public void test(String filename) throws IOException {
		try {
			ParseTree tree = open(filename).program();
			_walker.walk(_listener, tree);
			System.out.println(_listener.getProgram().flatten());
		} catch (Exception e) {
			Assert.fail(e.getMessage() + " on " + filename);
		}
	}

	// This method is run before each method annotated with @Test
	@Before
	public void setup() throws IOException {
		_listener = new DatalogListenerImpl();
		_walker = new ParseTreeWalker();
	}

	@Test
	public void testT1() throws IOException {
		test("/t1.logic");
	}
	@Test
	public void testT2() throws IOException {
		test("/t2.logic");
	}
	@Test
	public void testT3() throws IOException {
		test("/t3.logic");
	}
	@Test
	public void testT4() throws IOException {
		test("/t4.logic");
	}
	@Test
	public void testT5() throws IOException {
		test("/t5.logic");
	}
	@Test
	public void testT6() throws IOException {
		test("/t6.logic");
	}
	@Test
	public void testSample() throws IOException {
		test("/sample.logic");
	}


	@Test
	public void testA01() throws IOException {
		test("/analysis/cfg-tests.logic");
	}
	@Test
	public void testA02() throws IOException {
		test("/analysis/context-insensitive-declarations.logic");
	}
	@Test
	public void testA03() throws IOException {
		test("/analysis/context-insensitive.logic");
	}
	@Test
	public void testA04() throws IOException {
		test("/analysis/facts-declarations.logic");
	}
	@Test
	public void testA05() throws IOException {
		test("/analysis/flow-insensitivity-declarations.logic");
	}
	@Test
	public void testA06() throws IOException {
		test("/analysis/statistics-simple.logic");
	}
	@Test
	public void testA07() throws IOException {
		test("/analysis/tamiflex-declarations.logic");
	}
	@Test
	public void testA08() throws IOException {
		test("/analysis/tamiflex-fact-declarations.logic");
	}
	@Test
	public void testA09() throws IOException {
		test("/analysis/tamiflex.logic");
	}
}
