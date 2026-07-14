package org.clyze.doop.jimple

import groovy.cli.commons.CliBuilder
import groovy.transform.CompileStatic
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.GnuParser
import org.apache.commons.cli.Option

@CompileStatic
class Main {
	private static final String IN_JIMPLE_DIR_ARG = 'JIMPLE_DIRECTORY'
	private static final String IN_DB_DIR_ARG = 'DATABASE_DIRECTORY'
	private static final String OUT_DIR_ARG = 'OUTPUT_DIR'
	private static final String RELATION_ARG = 'RELATION'
	private static final String RELATION_TYPE_ARG = 'TYPE'

	private static String getUsage() {
		return "Usage: code-processor [OPTION]..."
	}

	private static void printHelpText() {
		println """\nExamples:
* Check that Jimple output can be parsed
  code-processor -j /path/to/jimple/directory
* Generate SARIF results for Doop output, relative to Jimple code:
  code-processor -j /path/to/jimple/directory -d /path/to/database -o output-dir
"""
	}

	static void main(String[] args) {
		def cli = new CliBuilder(
				parser: new GnuParser(),
				usage: usage,
				width: 100
		)

		Option inJimpleOpt = new Option('j', 'jimple-dir', true, 'Input Jimple directory.')
		inJimpleOpt.argName = IN_JIMPLE_DIR_ARG
		inJimpleOpt.required = true
		cli.options.addOption(inJimpleOpt)

		Option inDbOpt = new Option('d', 'database-dir', true, 'Input database directory.')
		inDbOpt.argName = IN_DB_DIR_ARG
		cli.options.addOption(inDbOpt)

		Option outDirOpt = new Option('o', 'out', true, 'Output metadata directory.')
		outDirOpt.argName = OUT_DIR_ARG
		cli.options.addOption(outDirOpt)

		if (!args) {
			cli.usage()
			printHelpText()
			return
		}

		try {
			CommandLine cl = cli.parser.parse(cli.options, args)
			String jimplePath = cl.getOptionValue('j')
			String dbPath = cl.getOptionValue('d')
			String outDir = cl.getOptionValue('o')
			new JimpleProcessor(jimplePath, dbPath ? new File(dbPath) : null, outDir ? new File(outDir) : null, "STANDALONE", true).process()
		} catch (Throwable t) {
			println("ERROR: ${t.message}")
			t.printStackTrace()
		}
	}
}

