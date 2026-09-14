package org.clyze.doop.utils

import groovy.transform.CompileStatic

/**
 * Rewrites Souffle-flavored Datalog (as produced by Doop's preprocessor) into a
 * dialect the FlowLog engine accepts. Each transformation is a standalone pass
 * over the source text; more are expected to be chained here.
 *
 * Transformation #1: variable names. Doop follows the LogicBlox convention of
 * prefixing every logic variable with '?' (e.g. "?method"), which Souffle
 * tolerates but FlowLog does not. The '?' is dropped from every identifier,
 * leaving the rest of the name untouched, so "?method" becomes "method".
 *
 * Transformation #2: .plan directives. FlowLog's grammar accepts a single
 * permutation per directive, whereas Souffle allows one per semi-naive version
 * ("<code>.plan 1:(2,1,3), 2:(3,2,1)</code>"); the multi-version form Doop uses
 * in ~40 places is a hard parse error. FlowLog also discards the version prefix
 * and simply permutes the rule body at parse time, so a directive written for
 * one of Souffle's delta variants would be applied unconditionally. Since a
 * join order is a performance hint that cannot change a program's results, the
 * safe course is to drop the directives altogether and let FlowLog join the
 * body in source order.
 *
 * Both rewrites are lexically aware: text inside a string literal or a comment
 * is preserved verbatim (e.g. cat(?x, "?") keeps its "?" argument, and a
 * commented-out "// .plan 1:(2,1)" is left alone).
 *
 * An instance holds the source text of one file and applies the passes to it in
 * the order requested, each returning the transformer so they can be chained;
 * {@link #writeTo(File)} ends the pipeline. Reading and writing the same file is
 * fine -- the text is held in memory for the lifetime of the instance:
 *
 * <pre>
 * new FlowLogTransformer(analysisFile)
 *         .stripVarPrefixes()
 *         .dropPlanDirectives()
 *         .writeTo(analysisFile)
 * </pre>
 *
 * An instance is a single-use, mutable pipeline and is not thread-safe. The
 * passes are also available as pure String-to-String static methods.
 */
@CompileStatic
class FlowLogTransformer {

	/** The source text being rewritten, replaced in place by each pass. */
	private String text

	/** Starts a pipeline over the contents of {@code inFile}, which is read immediately. */
	FlowLogTransformer(File inFile) {
		this.text = inFile.text
	}

	/** Applies the '?'-prefix strip to the held text. Returns this, for chaining. */
	FlowLogTransformer stripVarPrefixes() {
		text = stripVarPrefixes(text)
		return this
	}

	/** Applies the .plan removal to the held text. Returns this, for chaining. */
	FlowLogTransformer dropPlanDirectives() {
		text = dropPlanDirectives(text)
		return this
	}

	/** Ends the pipeline, writing the transformed text to {@code outFile}. */
	void writeTo(File outFile) {
		outFile.text = text
	}

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

	/**
	 * Removes every .plan directive from a single .dl source text. A directive
	 * that sits alone on its line takes the whole line with it; one that trails
	 * other text is excised in place, leaving that text untouched.
	 */
	static String dropPlanDirectives(String source) {
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
			} else if (c == ('.' as char) && planDirectiveEnd(source, i) >= 0) {
				int end = planDirectiveEnd(source, i)
				int lineStart = out.lastIndexOf('\n') + 1
				int lineEnd = blankLineRemainderEnd(source, end)
				if (lineEnd >= 0 && isBlankFrom(out, lineStart)) {
					// Nothing else shared the directive's line(s): drop them entirely.
					out.setLength(lineStart)
					i = lineEnd
				} else {
					i = end
				}
			} else {
				out.append(c)
				i++
			}
		}
		return out.toString()
	}

	private static final String PLAN_KEYWORD = '.plan'

	/**
	 * Returns the index just past the .plan directive starting at {@code start},
	 * or -1 if no well-formed directive starts there. Recognizes both FlowLog's
	 * native form ".plan (3,1,2)" and Souffle's versioned, comma-separated form
	 * ".plan 1:(2,1,3), 2:(3,2,1)". A directive may span several lines, as the
	 * four-version one in souffle-logic/main/context-sensitivity.dl does.
	 */
	private static int planDirectiveEnd(String s, int start) {
		if (!s.startsWith(PLAN_KEYWORD, start)) return -1
		// Not a directive when '.plan' is the tail of a qualified name, e.g. 'comp.plan'.
		if (start > 0 && isIdentifierPart(s.charAt(start - 1))) return -1
		int n = s.length()
		int i = start + PLAN_KEYWORD.length()
		if (i < n && isIdentifierPart(s.charAt(i))) return -1
		while (true) {
			i = skipWhitespace(s, i)
			// Optional Souffle version prefix: '<digits> :'
			int digitsEnd = i
			while (digitsEnd < n && Character.isDigit(s.charAt(digitsEnd))) digitsEnd++
			if (digitsEnd > i) {
				int colon = skipWhitespace(s, digitsEnd)
				if (colon >= n || s.charAt(colon) != (':' as char)) return -1
				i = skipWhitespace(s, colon + 1)
			}
			if (i >= n || s.charAt(i) != ('(' as char)) return -1
			int depth = 0
			while (i < n) {
				char c = s.charAt(i)
				i++
				if (c == ('(' as char)) depth++
				else if (c == (')' as char) && --depth == 0) break
			}
			if (depth != 0) return -1
			int next = skipWhitespace(s, i)
			// A comma continues the directive with a further versioned permutation.
			if (next < n && s.charAt(next) == (',' as char)) {
				i = next + 1
				continue
			}
			return i
		}
	}

	/**
	 * Index of the first non-whitespace character at or after {@code i}. Newlines are
	 * skipped along with spaces, so a directive whose permutations are spread over
	 * several lines is recognized as a whole.
	 */
	private static int skipWhitespace(String s, int i) {
		int n = s.length()
		int j = i
		while (j < n && Character.isWhitespace(s.charAt(j))) j++
		return j
	}

	/**
	 * If only whitespace separates {@code from} from the end of its line, returns the
	 * index just past that line's terminator (or the end of input); otherwise -1.
	 */
	private static int blankLineRemainderEnd(String s, int from) {
		int n = s.length()
		int i = from
		while (i < n) {
			char c = s.charAt(i)
			if (c == ('\n' as char)) return i + 1
			if (!Character.isWhitespace(c)) return -1
			i++
		}
		return n
	}

	/** Whether everything appended to {@code out} from {@code from} onwards is whitespace. */
	private static boolean isBlankFrom(StringBuilder out, int from) {
		for (int i = from; i < out.length(); i++) {
			if (!Character.isWhitespace(out.charAt(i))) return false
		}
		return true
	}

	private static boolean isIdentifierStart(char c) {
		return Character.isLetter(c) || c == ('_' as char)
	}

	private static boolean isIdentifierPart(char c) {
		return Character.isLetterOrDigit(c) || c == ('_' as char)
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
