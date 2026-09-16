package org.clyze.doop.utils

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

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
 * Transformation #3: statistics metrics. souffle-logic/addons/statistics/macros.dl
 * expands each metric into a Souffle body aggregate that FlowLog cannot express:
 *
 * <pre>Stats_Metrics("8.0", "call graph edges (INS)", c) :- c = count : { R(_, _) }.</pre>
 *
 * FlowLog aggregates in the rule head, and its count() counts DISTINCT VALUES
 * rather than rows, so the whole row has to be counted as a tuple -- which
 * requires naming every column, and therefore knowing the column count. That
 * count is visible only inside the atom, where the C preprocessor cannot reach,
 * which is why the rewrite happens here rather than in the macro. Running on the
 * preprocessed program also means every #ifdef around a metric has already been
 * resolved, so no guard is duplicated. The rule above becomes:
 *
 * <pre>.decl _MAgg_3(c:number)
 * _MAgg_3(count((v1, v2))) :- R(v1, v2).
 * _MetricCount("8.0", "call graph edges (INS)", c) :- _MAgg_3(c).
 * MetricDecl("8.0", "call graph edges (INS)").</pre>
 *
 * Each metric gets an aggregate relation to itself: sharing one relation between
 * metrics trips a FlowLog code-generation defect, recorded in macros.dl. The
 * MetricDecl registry, _MetricCount and the rules assembling Stats_Metrics from
 * them live in macros.dl, under its FLOWLOG_ENGINE branch; the _MetricCount and
 * MetricDecl names are a contract between this pass and that file.
 * A metric rule that does not match the expected shape is an error rather than
 * a silent pass-through, so a change to the macro cannot quietly drop metrics.
 *
 * Transformation #4: inline declarations. Souffle lets a .decl carry an "inline"
 * qualifier, telling it to expand the relation at its use sites rather than
 * materialise it. FlowLog's grammar has no such qualifier and rejects the
 * declaration outright. Like a join order, it is an evaluation directive that
 * cannot change what a program computes, so the qualifier is simply dropped and
 * FlowLog materialises the relation instead -- which may cost memory on a large
 * one, but cannot change the result.
 *
 * All four rewrites are lexically aware: text inside a string literal or a
 * comment is preserved verbatim (e.g. cat(?x, "?") keeps its "?" argument, a
 * commented-out "// .plan 1:(2,1)" is left alone, and an aggregate written out
 * inside a string is not mistaken for code).
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
 *         .rewriteStatsMetrics()
 *         .dropInlineQualifiers()
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

	/** Rewrites the Souffle statistics metrics into FlowLog form. Returns this, for chaining. */
	FlowLogTransformer rewriteStatsMetrics() {
		text = rewriteStatsMetrics(text)
		return this
	}

	/** Drops the Souffle "inline" qualifier from declarations. Returns this, for chaining. */
	FlowLogTransformer dropInlineQualifiers() {
		text = dropInlineQualifiers(text)
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

	/**
	 * Removes the Souffle "inline" qualifier from every declaration in a .dl source
	 * text, leaving the declaration itself untouched. A source with no such
	 * qualifier is returned unchanged.
	 */
	static String dropInlineQualifiers(String source) {
		String[] lines = source.split('\n', -1)
		StringBuilder out = new StringBuilder(source.length())
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) out.append('\n')
			Matcher m = INLINE_DECL.matcher(lines[i])
			if (m.matches()) {
				out.append(m.group(1))
				if (m.group(2) != null) out.append(' ').append(m.group(2))
			} else {
				out.append(lines[i])
			}
		}
		return out.toString()
	}

	/**
	 * A declaration carrying the "inline" qualifier. Group 1 is the declaration
	 * without it; any trailing comment is kept.
	 */
	private static final Pattern INLINE_DECL = ~/^(\s*\.decl\s+[^\/]*\))\s+inline\s*(\/\/.*)?$/

	/**
	 * Rewrites every expanded statistics metric in a .dl source text into the
	 * head-aggregation form FlowLog accepts. A source with no metrics (an analysis
	 * run with --stats none) is returned unchanged.
	 *
	 * <p>Postcondition: no Souffle body aggregate survives anywhere in the program.
	 * Only metrics written with NewMetricMacro are rewritten here; any other aggregate
	 * has to be ported by hand, and is reported rather than left for the FlowLog
	 * compiler to reject against generated code.
	 *
	 * @throws IllegalStateException if a Stats_Metrics counting rule is not in the
	 *         shape macros.dl produces, or if any Souffle body aggregate remains --
	 *         better a build failure than logic silently lost.
	 */
	static String rewriteStatsMetrics(String source) {
		String[] lines = source.split('\n', -1)
		StringBuilder out = new StringBuilder(source.length() + 1024)
		int metricIndex = 0
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) out.append('\n')
			String line = lines[i]
			Matcher m = METRIC_RULE.matcher(line)
			if (m.matches()) {
				metricIndex++
				out.append(metricRule(metricIndex, m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), line))
			} else {
				if (line.contains(STATS_METRICS) && line.contains('count')) {
					throw new IllegalStateException(
							"FlowLogTransformer: Stats_Metrics rule not in the shape macros.dl produces, " +
							"cannot rewrite for FlowLog: " + line.trim())
				}
				out.append(line)
			}
		}
		String rewritten = out.toString()
		assertNoSouffleAggregates(rewritten)
		return rewritten
	}

	private static final String STATS_METRICS = 'Stats_Metrics('

	/**
	 * A Souffle body aggregate: {@code = <agg> [expr] :}. FlowLog has no such form,
	 * so none may survive this pass.
	 */
	private static final Pattern SOUFFLE_BODY_AGGREGATE = ~/=\s*(count|min|max|sum|mean|avg)\b[^:\n]*:/

	/**
	 * Statement-level backstop for the line-level check above. A metric rule written
	 * directly rather than through NewMetricMacro spreads its aggregates over several
	 * lines, so no single line carries both markers and the line check cannot see it.
	 * Sweeping the rewritten text catches those, and any other Souffle aggregate that
	 * was never ported, with a message naming the line instead of leaving the FlowLog
	 * compiler to fail on generated code.
	 */
	private static void assertNoSouffleAggregates(String source) {
		Matcher m = SOUFFLE_BODY_AGGREGATE.matcher(blankLiteralsAndComments(source))
		if (!m.find()) return
		int lineNo = 1
		for (int i = 0; i < m.start(); i++) {
			if (source.charAt(i) == ('\n' as char)) lineNo++
		}
		int from = source.lastIndexOf('\n' as String, m.start()) + 1
		int to = source.indexOf('\n' as String, m.start())
		String line = (to < 0 ? source.substring(from) : source.substring(from, to)).trim()
		throw new IllegalStateException(
				"FlowLogTransformer: Souffle body aggregate survives at line ${lineNo}, which FlowLog cannot " +
				"parse: ${line} -- FlowLog has no body aggregate form, so the rule has to be ported to head " +
				"aggregation under #ifdef FLOWLOG_ENGINE, keeping the Souffle form in the #else. Metrics written " +
				"with NewMetricMacro are rewritten automatically by this pass; every other aggregate is ported by " +
				"hand. See souffle-logic/addons/statistics/macros.dl for what the ported shapes look like.")
	}

	/**
	 * A copy of {@code source} with the inside of string literals and comments blanked
	 * out, so a scan cannot match text that is not code. Offsets and line breaks are
	 * preserved so positions still refer to the original.
	 */
	private static String blankLiteralsAndComments(String source) {
		StringBuilder out = new StringBuilder(source)
		int i = 0
		int n = source.length()
		char space = ' ' as char
		while (i < n) {
			char c = source.charAt(i)
			if (c == ('"' as char)) {
				i++
				while (i < n) {
					char d = source.charAt(i)
					if (d == ('"' as char)) { i++; break }
					if (d != ('\n' as char)) out.setCharAt(i, space)
					if (d == ('\\' as char) && i + 1 < n) {
						if (source.charAt(i + 1) != ('\n' as char)) out.setCharAt(i + 1, space)
						i += 2
						continue
					}
					i++
				}
			} else if (c == ('/' as char) && i + 1 < n && source.charAt(i + 1) == ('/' as char)) {
				while (i < n && source.charAt(i) != ('\n' as char)) { out.setCharAt(i, space); i++ }
			} else if (c == ('/' as char) && i + 1 < n && source.charAt(i + 1) == ('*' as char)) {
				out.setCharAt(i, space); out.setCharAt(i + 1, space)
				i += 2
				while (i < n) {
					if (source.charAt(i) == ('*' as char) && i + 1 < n && source.charAt(i + 1) == ('/' as char)) {
						out.setCharAt(i, space); out.setCharAt(i + 1, space)
						i += 2
						break
					}
					if (source.charAt(i) != ('\n' as char)) out.setCharAt(i, space)
					i++
				}
			} else {
				i++
			}
		}
		return out.toString()
	}

	/**
	 * One expanded metric: {@code Stats_Metrics(<order>, <msg>, c) :- c = count : { R(_, ...) }.}
	 * The groups are the leading indent, the two quoted literals, the relation name
	 * and the raw argument list.
	 */
	private static final Pattern METRIC_RULE = ~/^(\s*)Stats_Metrics\(\s*("(?:[^"\\]|\\.)*")\s*,\s*("(?:[^"\\]|\\.)*")\s*,\s*c\s*\)\s*:-\s*c\s*=\s*count\s*:\s*\{\s*([A-Za-z_][A-Za-z0-9_.]*)\s*\(([^)]*)\)\s*}\s*\.\s*$/

	/**
	 * Builds the FlowLog rules for one metric: a private aggregate relation holding a
	 * single rule, a plain rule copying its value into _MetricCount, and the registry
	 * entry. The aggregate gets a relation to itself deliberately -- see the KNOWN
	 * FLOWLOG DEFECT note in souffle-logic/addons/statistics/macros.dl.
	 */
	private static String metricRule(int index, String indent, String order, String msg,
	                                 String relation, String args, String original) {
		int arity = metricArity(args, original)
		String agg = "_MAgg_${index}"
		String head
		if (arity == 0) {
			// A nullary relation holds no columns to count: it is present or it is not.
			head = "${indent}${agg}(1) :- ${relation}()."
		} else {
			StringBuilder vars = new StringBuilder()
			for (int i = 1; i <= arity; i++) {
				if (i > 1) vars.append(', ')
				vars.append('v').append(i)
			}
			String varList = vars.toString()
			// One column is already unique per row, so it needs no tuple wrapper; more
			// than one does, because FlowLog's count() counts distinct values of its
			// argument.
			String counted = arity == 1 ? varList : "(${varList})"
			head = "${indent}${agg}(count(${counted})) :- ${relation}(${varList})."
		}
		return "${indent}.decl ${agg}(c:number)\n" +
				head + "\n" +
				"${indent}_MetricCount(${order}, ${msg}, c) :- ${agg}(c).\n" +
				"${indent}MetricDecl(${order}, ${msg})."
	}

	/** Number of columns in a metric body, which macros.dl guarantees are all {@code _}. */
	private static int metricArity(String args, String original) {
		String trimmed = args.trim()
		if (trimmed.isEmpty()) return 0
		String[] parts = trimmed.split(',', -1)
		for (String part : parts) {
			if (part.trim() != '_') {
				throw new IllegalStateException(
						"FlowLogTransformer: metric body argument is not '_', so the row cannot be " +
						"counted as a tuple: " + original.trim())
			}
		}
		return parts.length
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
