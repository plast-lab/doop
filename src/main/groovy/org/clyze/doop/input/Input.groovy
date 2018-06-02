package org.clyze.doop.input

/**
 * The input to an analysis: a string that may correspond to one or more files.
 */
interface Input {

	String name()

	Set<File> files()
}
