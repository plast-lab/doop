package org.clyze.doop.input

/**
 * The input to an analysis: a string that may correspond to one or more files.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
interface Input {
    String name();
    Set<File> files();
}
