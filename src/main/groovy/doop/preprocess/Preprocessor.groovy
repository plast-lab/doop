package doop.preprocess

import doop.Analysis

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 18/7/2014
 *
 * The preprocessor for generating the logic files based on the given analysis.
 * This is experimental and may be removed.
 */
interface Preprocessor {
    void init()
    void preprocess(Analysis analysis, String basePath, String input, String output, String... includes)
}
