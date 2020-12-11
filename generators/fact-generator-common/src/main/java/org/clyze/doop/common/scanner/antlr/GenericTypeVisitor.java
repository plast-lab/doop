// Generated from GenericType.g4 by ANTLR 4.7.2
package org.clyze.doop.common.scanner.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link GenericTypeParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
@SuppressWarnings("JavadocReference")
public interface GenericTypeVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link GenericTypeParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(GenericTypeParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GenericTypeParser#simpleType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSimpleType(GenericTypeParser.SimpleTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GenericTypeParser#genericType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericType(GenericTypeParser.GenericTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GenericTypeParser#typeList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeList(GenericTypeParser.TypeListContext ctx);
}