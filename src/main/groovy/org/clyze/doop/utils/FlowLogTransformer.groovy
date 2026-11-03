package org.clyze.doop.utils

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Rewrites Souffle-flavored Datalog (as produced by Doop's preprocessor) into a
 * dialect the FlowLog engine accepts. Each transformation is a standalone pass
 * over the source text; more are expected to be chained here.
 *
 * Transformation #1: .plan directives. FlowLog's grammar accepts a single
 * permutation per directive, whereas Souffle allows one per semi-naive version
 * ("<code>.plan 1:(2,1,3), 2:(3,2,1)</code>"); the multi-version form Doop uses
 * in ~40 places is a hard parse error. FlowLog also discards the version prefix
 * and simply permutes the rule body at parse time, so a directive written for
 * one of Souffle's delta variants would be applied unconditionally. Since a
 * join order is a performance hint that cannot change a program's results, the
 * safe course is to drop the directives altogether and let FlowLog join the
 * body in source order.
 *
 * Transformation #2: inline declarations. Souffle lets a .decl carry an "inline"
 * qualifier, telling it to expand the relation at its use sites rather than
 * materialise it. FlowLog's grammar has no such qualifier and rejects the
 * declaration outright. Like a join order, it is an evaluation directive that
 * cannot change what a program computes, so the qualifier is simply dropped and
 * FlowLog materialises the relation instead -- which may cost memory on a large
 * one, but cannot change the result.
 *
 * Transformation #3: aggregate wildcards. Since flowlog-compiler 0.8.0 FlowLog
 * accepts Souffle's body aggregate ("<code>c = count : { R(_, _) }</code>") as
 * written, with one semantic difference: a "_" inside the braces projects the
 * column away, because FlowLog counts the distinct bindings of the NAMED
 * variables, whereas Souffle counts a lone atom's rows. So Souffle's idiom for
 * "how many rows" would count 1 -- and an aggregate whose body binds no variable
 * at all does not even compile (a codegen defect in 0.8.0). Naming every "_" of a
 * lone-atom <code>count</code>/<code>sum</code> body restores Souffle's meaning
 * and sidesteps the defect:
 *
 * <pre>c = count : { R(_, _) }       becomes    c = count : { R(?_w1, ?_w2) }
 * ?n = count : R(_, ?k)          becomes    ?n = count : R(?_w3, ?k)</pre>
 *
 * Only a body that is a single atom is touched. With two or more atoms Souffle
 * projects the "_" away as well, so the two engines already agree and naming the
 * column would change the count. <code>min</code> and <code>max</code> are
 * idempotent, so a "_" cannot change their value and they are left alone.
 *
 * Transformation #4: nullary metrics. A statistics metric over a relation with
 * no columns -- souffle-logic/addons/statistics/macros.dl expands
 * <code>NewMetricMacro("26.10", "obfuscation (boolean)", R())</code> into
 * <code>Stats_Metrics("26.10", "obfuscation (boolean)", c) :- c = count : { R() }.</code>
 * -- has no column to name, so #3 cannot help it and FlowLog cannot compile it.
 * It is rewritten into a registry fact and a presence rule:
 *
 * <pre>MetricDecl("26.10", "obfuscation (boolean)").
 * _MetricOn("26.10", "obfuscation (boolean)") :- R().</pre>
 *
 * which the FLOWLOG_ENGINE branch of macros.dl completes into the 1 or 0 that
 * Souffle reports. The MetricDecl and _MetricOn names and shapes are a contract
 * between this pass and that file. Only the macro's exact expansion is
 * recognised; a nullary aggregate written any other way is left for the FlowLog
 * compiler to reject.
 *
 * Transformation #5: records. Souffle spells a record type
 * "<code>[ f:T, g:U ]</code>" and a record value "<code>[?a, ?b]</code>". FlowLog
 * has the same construct under the name tuple, spelled "<code>( f:T, g:U )</code>"
 * and "<code>(?a, ?b)</code>", with "<code>(?a,)</code>" for a value of one field
 * (a 1-tuple type takes no trailing comma). Both are structural values -- equal
 * when their fields are, usable as join keys and relation columns -- so the
 * rewrite is purely a spelling change and cannot alter a program's results.
 * FlowLog will not adopt Souffle's spelling: its grammar reserves brackets for a
 * future array type. The only visible difference is in the output files, where
 * Souffle prints "<code>[a, b]</code>" and FlowLog "<code>(a, b)</code>".
 *
 * Where Doop's logic uses records (inventory of souffle-logic as of 2026-11-03,
 * comments and strings excluded):
 * <ul>
 * <li>Every context-sensitive configuration -- the 17 analysis.dl files of the
 *     1- and 2-call-site-sensitive, 1- to 4-object-sensitive, 1- to 3-type-sensitive
 *     analyses, their +heap variants, and context-insensitive-plusplus -- declares
 *     Context and/or HContext as a record of 1 to 4 symbol-typed fields (31 .type
 *     declarations) and builds or destructures them only through body equalities,
 *     "<code>?ctx = [?a, ?b]</code>" (95 sites, 2 to 8 per file). This pass covers
 *     all of them; the 1-call-site-sensitive program checks and runs on
 *     flowlog-compiler 0.8.0 with no other change.</li>
 * <li>Under HEAPDLS the same files also write a record literal directly as a head
 *     argument, "<code>DynamicContextToContext([?a, ?b], ?dynCtx) :- ...</code>"
 *     (12 sites in 7 files), which FlowLog accepts once rewritten, and in
 *     2-object-sensitive+heap and 2-type-sensitive+heap as a body atom argument,
 *     "<code>isContext([?v, ?any])</code>" (2 sites), which FlowLog's grammar does
 *     not allow in any spelling (a body atom argument must be a constant, a
 *     variable or "_"). Those two are rewritten here all the same and left for
 *     the compiler to reject; they need a hand rewrite into a variable plus a
 *     destructuring equality.</li>
 * <li>The shared logic under main/, basic/, facts/ and the remaining addons has
 *     no record at all.</li>
 * <li>Not covered, and outside the port: context-insensitive-plusplus also needs
 *     a union type, which FlowLog lacks; addons/symbolic-reasoning,
 *     analyses/sound-may-point-to, analyses/xtractor and the deprecated analyses
 *     use recursive records and <code>nil</code>, which FlowLog tuples cannot
 *     express (its type registry rejects recursion and it has no nil). The
 *     python/ logic mirrors the Java call-site configurations.</li>
 * </ul>
 *
 * All rewrites are lexically aware: text inside a string literal or a comment is
 * preserved verbatim (a commented-out "// .plan 1:(2,1)" is left alone, an
 * "inline" inside a string or a trailing comment is not mistaken for the
 * qualifier, and an aggregate written out inside a string is not mistaken for
 * code, nor is a bracket inside a string or a comment mistaken for a record).
 *
 * An instance holds the source text of one file and applies the passes to it in
 * the order requested, each returning the transformer so they can be chained;
 * {@link #writeTo(File)} ends the pipeline. Reading and writing the same file is
 * fine -- the text is held in memory for the lifetime of the instance:
 *
 * <pre>
 * new FlowLogTransformer(analysisFile)
 *         .dropPlanDirectives()
 *         .dropInlineQualifiers()
 *         .rewriteNullaryMetrics()
 *         .nameAggregateWildcards()
 *         .recordsToTuples()
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

	/** Applies the .plan removal to the held text. Returns this, for chaining. */
	FlowLogTransformer dropPlanDirectives() {
		text = dropPlanDirectives(text)
		return this
	}

	/** Drops the Souffle "inline" qualifier from declarations. Returns this, for chaining. */
	FlowLogTransformer dropInlineQualifiers() {
		text = dropInlineQualifiers(text)
		return this
	}

	/** Rewrites nullary statistics metrics into the MetricDecl/_MetricOn form. Returns this, for chaining. */
	FlowLogTransformer rewriteNullaryMetrics() {
		text = rewriteNullaryMetrics(text)
		return this
	}

	/** Names the wildcards of lone-atom count/sum bodies. Returns this, for chaining. */
	FlowLogTransformer nameAggregateWildcards() {
		text = nameAggregateWildcards(text)
		return this
	}

	/** Rewrites Souffle records into FlowLog tuples. Returns this, for chaining. */
	FlowLogTransformer recordsToTuples() {
		text = recordsToTuples(text)
		return this
	}

	/** Ends the pipeline, writing the transformed text to {@code outFile}. */
	void writeTo(File outFile) {
		outFile.text = text
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
	 * Rewrites every expanded NULLARY statistics metric in a .dl source text into
	 * the MetricDecl fact and _MetricOn rule that macros.dl completes. Metrics over
	 * a relation with columns are not touched here; {@link #nameAggregateWildcards}
	 * makes those count rows. A source with no nullary metric is returned unchanged.
	 */
	static String rewriteNullaryMetrics(String source) {
		String[] lines = source.split('\n', -1)
		StringBuilder out = new StringBuilder(source.length() + 256)
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) out.append('\n')
			String line = lines[i]
			Matcher m = NULLARY_METRIC_RULE.matcher(line)
			if (m.matches()) {
				String indent = m.group(1)
				String order = m.group(2)
				String msg = m.group(3)
				String relation = m.group(4)
				out.append("${indent}MetricDecl(${order}, ${msg}).\n")
				out.append("${indent}_MetricOn(${order}, ${msg}) :- ${relation}().")
			} else {
				out.append(line)
			}
		}
		return out.toString()
	}

	/**
	 * One expanded metric over a nullary relation:
	 * {@code Stats_Metrics(<order>, <msg>, c) :- c = count : { R() }.}
	 * The groups are the leading indent, the two quoted literals and the relation name.
	 */
	private static final Pattern NULLARY_METRIC_RULE = ~/^(\s*)Stats_Metrics\(\s*("(?:[^"\\]|\\.)*")\s*,\s*("(?:[^"\\]|\\.)*")\s*,\s*c\s*\)\s*:-\s*c\s*=\s*count\s*:\s*\{\s*([A-Za-z_][A-Za-z0-9_.]*)\s*\(\s*\)\s*}\s*\.\s*$/

	/**
	 * Names every "_" argument of a lone-atom count/sum aggregate body in a .dl
	 * source text, so FlowLog counts rows as Souffle does. Both the braced form
	 * {@code count : { R(_, ?k) }} and the brace-less {@code count : R(_, ?k)} are
	 * recognised, on one line or several. A body of two or more atoms, and any
	 * min/max aggregate, is left as it is. A source with no such wildcard is
	 * returned unchanged.
	 *
	 * <p>The fresh names are {@code ?_w1}, {@code ?_w2}, ... numbered through the
	 * whole text. Doop's own variables never start with "?_", so they cannot
	 * collide, and a name is one variable throughout a FlowLog rule, so each
	 * wildcard must get a name of its own.
	 */
	static String nameAggregateWildcards(String source) {
		// Match on a copy with literals and comments blanked, edit the original at
		// the same offsets: the copy preserves every position.
		String blanked = blankLiteralsAndComments(source)
		Matcher m = LONE_ATOM_AGGREGATE.matcher(blanked)
		StringBuilder out = new StringBuilder(source)
		List<int[]> argSpans = []
		while (m.find()) {
			// Group 1 holds the braced form's arguments, group 3 the brace-less form's.
			int argGroup = m.group(1) != null ? 1 : 3
			int argsStart = m.start(argGroup)
			String args = m.group(argGroup)
			int from = 0
			while (true) {
				int comma = args.indexOf(',' as String, from)
				int to = comma < 0 ? args.length() : comma
				if (args.substring(from, to).trim() == '_') {
					int us = argsStart + from + args.substring(from, to).indexOf('_' as String)
					argSpans.add([us, us + 1] as int[])
				}
				if (comma < 0) break
				from = comma + 1
			}
		}
		// Replace back to front so earlier offsets stay valid.
		int counter = argSpans.size()
		for (int k = argSpans.size() - 1; k >= 0; k--) {
			int[] span = argSpans[k]
			out.replace(span[0], span[1], "?_w${counter}".toString())
			counter--
		}
		return out.toString()
	}

	/**
	 * A count/sum aggregate whose body is a single atom. The keyword must not be
	 * the tail of a variable or identifier ({@code ?count}), the optional target
	 * ({@code sum ?n}) runs to the colon, and the colon must not open a rule
	 * ({@code :-}). Group 1 holds the arguments of the braced form, group 3 those
	 * of the brace-less form (group 2), which must be followed by the end of its
	 * predicate.
	 */
	private static final Pattern LONE_ATOM_AGGREGATE = ~/(?<![?A-Za-z0-9_])(?:count|sum)\b(?:\s+[^:{};]*?)?\s*:(?!-)\s*(?:\{\s*[A-Za-z_][A-Za-z0-9_.]*\s*\(([^(){}]*)\)\s*}|([A-Za-z_][A-Za-z0-9_.]*\s*\(([^(){}]*)\))(?=\s*[,.]))/

	/**
	 * Rewrites every Souffle record type and record value in a .dl source text
	 * into FlowLog's tuple spelling: the brackets become parentheses, and a
	 * value of exactly one field gets the trailing comma that tells a 1-tuple
	 * from a parenthesised value. A one-field type ({@code .type C = [ f:T ]})
	 * gets no comma, as FlowLog's tuple type syntax takes none. Nested records
	 * are rewritten at every level; the field count of a record is taken at its
	 * own level, so a comma inside a nested record or a function call does not
	 * count. An empty pair of brackets is rewritten as it is. A source with no
	 * record is returned unchanged.
	 */
	static String recordsToTuples(String source) {
		// Match on a copy with literals and comments blanked, edit the original at
		// the same offsets: the copy preserves every position.
		String blanked = blankLiteralsAndComments(source)
		int n = blanked.length()
		// Open records, innermost on top: [open offset, field count so far, paren depth at open].
		Deque<int[]> open = new ArrayDeque<int[]>()
		// Closed records: [open offset, close offset, field count, 1 if a type declaration].
		List<int[]> records = []
		int paren = 0
		for (int i = 0; i < n; i++) {
			char c = blanked.charAt(i)
			if (c == ('[' as char)) {
				open.push([i, 0, paren] as int[])
			} else if (c == ('(' as char)) {
				paren++
			} else if (c == (')' as char)) {
				paren--
			} else if (c == (',' as char)) {
				if (!open.isEmpty() && open.peek()[2] == paren) open.peek()[1]++
			} else if (c == (']' as char)) {
				if (open.isEmpty()) continue
				int[] rec = open.pop()
				boolean empty = blanked.substring(rec[0] + 1, i).trim().isEmpty()
				int fields = empty ? 0 : rec[1] + 1
				int lineStart = blanked.lastIndexOf('\n', rec[0]) + 1
				boolean isType = TYPE_DECL_PREFIX.matcher(blanked.substring(lineStart, rec[0])).matches()
				records.add([rec[0], i, fields, isType ? 1 : 0] as int[])
			}
		}
		if (records.isEmpty()) return source
		StringBuilder out = new StringBuilder(source)
		for (int[] rec : records) {
			out.setCharAt(rec[0], '(' as char)
			out.setCharAt(rec[1], ')' as char)
		}
		// Trailing commas lengthen the text: insert them back to front so earlier
		// offsets stay valid.
		records.sort { int[] a, int[] b -> b[1] <=> a[1] }
		for (int[] rec : records) {
			if (rec[2] == 1 && rec[3] == 0) out.insert(rec[1], ',' as char)
		}
		return out.toString()
	}

	/** The start of a record type declaration, up to its opening bracket: {@code .type Name = }. */
	private static final Pattern TYPE_DECL_PREFIX = ~/^\s*\.type\s+[A-Za-z_][A-Za-z0-9_]*\s*=\s*$/

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
