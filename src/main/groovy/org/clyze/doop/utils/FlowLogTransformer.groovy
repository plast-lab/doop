package org.clyze.doop.utils

import groovy.transform.CompileStatic

/**
 * Rewrites Souffle-flavored Datalog (as produced by Doop's preprocessor) into a
 * dialect the FlowLog engine accepts. Currently a single transformation is
 * implemented; more are expected to be chained here.
 *
 * Transformation #1: variable names. Doop follows the LogicBlox convention of
 * prefixing every logic variable with '?' (e.g. "?method"), which Souffle
 * tolerates but FlowLog does not. The '?' is dropped from every identifier,
 * leaving the rest of the name untouched, so "?method" becomes "method".
 *
 * The rewrite is lexically aware: '?' occurring inside a string literal or a
 * comment is preserved verbatim (e.g. cat(?x, "?") keeps its "?" argument).
 */
@CompileStatic
class FlowLogTransformer {

	/** Strips the '?' prefix from all identifiers in a single .dl source text. */
	static String stripVarPrefixes(String source) {
		StringBuilder out = new StringBuilder(source.length())
		int i = 0
		int n = source.length()
		while (i < n) {
			char c = source.charAt(i)
			if (c == ('"' as char)) {
				i = copyStringLiteral(source, i, out)
			} else if (c == ('/' as char) && i + 1 < n && source.charAt(i + 1) == ('/' as char)) {
				i = copyLineComment(source, i, out)
			} else if (c == ('/' as char) && i + 1 < n && source.charAt(i + 1) == ('*' as char)) {
				i = copyBlockComment(source, i, out)
			} else if (c == ('?' as char) && i + 1 < n && isIdentifierStart(source.charAt(i + 1))) {
				// Drop the '?' -- the identifier that follows is copied as-is.
				i++
			} else {
				out.append(c)
				i++
			}
		}
		return out.toString()
	}

	/** Reads {@code inFile}, strips the '?' prefixes and writes the result to {@code outFile}. */
	static void stripVarPrefixes(File inFile, File outFile) {
		outFile.text = stripVarPrefixes(inFile.text)
	}

	private static boolean isIdentifierStart(char c) {
		return Character.isLetter(c) || c == ('_' as char)
	}

	/** Copies a "..."-delimited literal (honoring \-escapes) and returns the index past it. */
	private static int copyStringLiteral(String s, int start, StringBuilder out) {
		int n = s.length()
		out.append(s.charAt(start))
		int i = start + 1
		while (i < n) {
			char c = s.charAt(i)
			out.append(c)
			i++
			if (c == ('\\' as char)) {
				if (i < n) {
					out.append(s.charAt(i))
					i++
				}
			} else if (c == ('"' as char)) {
				break
			}
		}
		return i
	}

	/** Copies a // comment up to (not including) the line terminator. */
	private static int copyLineComment(String s, int start, StringBuilder out) {
		int i = start
		int n = s.length()
		while (i < n && s.charAt(i) != ('\n' as char)) {
			out.append(s.charAt(i))
			i++
		}
		return i
	}

	/** Copies a block comment, including its terminator (or up to EOF if unterminated). */
	private static int copyBlockComment(String s, int start, StringBuilder out) {
		int n = s.length()
		out.append('/*')
		int i = start + 2
		while (i < n) {
			if (s.charAt(i) == ('*' as char) && i + 1 < n && s.charAt(i + 1) == ('/' as char)) {
				out.append('*/')
				return i + 2
			}
			out.append(s.charAt(i))
			i++
		}
		return i
	}
}
