package org.clyze.doop.common.scanner.antlr;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.scanner.antlr.GenericTypeParser.TypeContext;

import static org.clyze.doop.common.PredicateFile.*;

public class PrintVisitor extends GenericTypeBaseVisitor<String> {
	private final Database _db;

	public PrintVisitor(Database db) {
		_db = db;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>The default implementation returns the result of calling
	 * {@link #visitChildren} on {@code ctx}.</p>
	 */
	@Override
	public String visitType(TypeContext ctx) {
		return visitChildren(ctx);
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>The default implementation returns the result of calling
	 * {@link #visitChildren} on {@code ctx}.</p>
	 */
	@Override
	public String visitSimpleType(GenericTypeParser.SimpleTypeContext ctx) {
		visitChildren(ctx);
		return  ctx.getText();
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>The default implementation returns the result of calling
	 * {@link #visitChildren} on {@code ctx}.</p>
	 */
	@Override
	public String visitGenericType(GenericTypeParser.GenericTypeContext ctx) {
		int index = 0;
		for (TypeContext typeContext : ctx.typeList().type()) {
			String genericTYpe = ctx.getText().replace(",", ", ");

			_db.add(GENERIC_TYPE_PARAMETERS, genericTYpe, String.valueOf(index), typeContext.getText());
			_db.add(GENERIC_TYPE_ERASED_TYPE, genericTYpe, ctx.children.get(0).getText());
			index++;
		}

		return visitChildren(ctx);
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>The default implementation returns the result of calling
	 * {@link #visitChildren} on {@code ctx}.</p>
	 */
	@Override public String visitTypeList(GenericTypeParser.TypeListContext ctx) {
		return visitChildren(ctx);
	}
}
