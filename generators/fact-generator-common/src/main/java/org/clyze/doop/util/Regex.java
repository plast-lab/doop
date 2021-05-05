package org.clyze.doop.util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A regular exrpession that is either a constant piece of text or a wildcard-ending prefix.
 */
public class Regex {
    /**
     * The text part of the regular expression.
     */
    public final String text;
    /**
     * If true, this is a wildcard-ending expression.
     */
    public final boolean isWildcard;
    /**
     * If true, this wildcard is a package prefix, otherwise it matches packages exactly.
     */
    public boolean isPrefix = false;
    /**
     * Set to true during reduction.
     */
    public boolean deleted = false;

    private Regex(String text, boolean isWildcard) {
        this.text = text;
        this.isWildcard = isWildcard;
    }

    /**
     * Create a regex matching an exact string.
     *
     * @param text the string to match
     * @return the regex object
     */
    static Regex exact(String text) {
        return new Regex(text, false);
    }

    /**
     * Create a regex ending in a wildcard.
     *
     * @param text the string to match
     * @return the regex object
     */
    static Regex wild(String text) {
        return new Regex(text, true);
    }

    @Override
    public String toString() {
        return isWildcard ? (text + (isPrefix ? ".**" : ".*")) : text;
    }

    /**
     * Parse an "application regex" string.
     * @param s  the string to parse
     * @return   the regex objects contained in the string
     */
    @SuppressWarnings("unused")
    public static Collection<Regex> fromAppRegex(String s) {
        Collection<Regex> ret = new ArrayList<>();
        for (String r : s.split(":")) {
            int aaIdx = r.indexOf(".**");
            if (aaIdx >= 0) {
                Regex regex = Regex.wild(r.substring(0, aaIdx));
                regex.isPrefix = true;
                ret.add(regex);
            } else {
                int aIdx = r.indexOf(".*");
                ret.add((aIdx >= 0) ? Regex.wild(r.substring(0, aIdx)) : Regex.exact(r));
            }
        }
        return ret;
    }
}
