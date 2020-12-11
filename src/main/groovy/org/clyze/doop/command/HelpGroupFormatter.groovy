package org.clyze.doop.command

import groovy.transform.CompileStatic
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

/**
 * A CliBuilder formatter that groups options per group when printing
 * the usage message. Assumes long options exist for all options.
 */
@CompileStatic
class HelpGroupFormatter extends HelpFormatter {

    static final String CONFIGURATION_OPTIONS = "Configuration options"

    @Override
    void printOptions(PrintWriter pw, int width, Options options, int leftPad, int descPad) {
        // This set gathers "null" Doop options and miscellaneous CliBuilder options.
        Options nullGroupOpts = new Options()
        List<GOption> otherGroupOpts = [] as List<GOption>
        (options.options as Collection<Option>).each { opt ->
            if ((opt instanceof GOption) && (opt.group != null)) {
                otherGroupOpts.add(opt as GOption)
            } else {
                nullGroupOpts.addOption(opt)
            }
        }

        // Measure widths per group to fix them when calling the printer on each.
        Map<String, Tuple2<Options, Integer>> groups = [:]
        if (nullGroupOpts.options.size() > 0)
            groups.put(CONFIGURATION_OPTIONS, getOptionsInfo(nullGroupOpts))
        otherGroupOpts.groupBy { it.group }.sort().each { group, optsList ->
            def opts = new Options()
            optsList.each { opts.addOption(it) }
            groups.put(group, getOptionsInfo(opts))
        }

        int maxOptWidth = (groups.values() as Collection<Tuple2<Options, Integer>>).collect { optsInfo -> optsInfo.v2 }.max()
        for (Map.Entry<String, Tuple2<Options, Integer>> entry : groups.entrySet()) {
            pw.println()
            printGroup(pw, entry.key, width, maxOptWidth, entry.value, leftPad, descPad)
        }
    }

    static Tuple2<Options, Integer> getOptionsInfo(org.apache.commons.cli.Options opts) {
        return new Tuple2<>(opts, calcMaxWidth(opts))
    }

    void printGroup(PrintWriter pw, String group, int width, int maxOptWidth,
                    Tuple2<Options, Integer> optsInfo, int leftPad, int descPad) {
        pw.println "== ${group} =="
        int gWidth = maxOptWidth - optsInfo.v2
        def opts = optsInfo.v1
        super.printOptions(pw, width, opts, leftPad, descPad + gWidth)
    }

    /**
     * Calculates the maximum width of the (short+long) option line prefixes.
     * @param opts      the options
     * @return          the size of the longest option line (excluding description)
     */
    static int calcMaxWidth(org.apache.commons.cli.Options opts) {
        (opts.options as Collection<Option>).collect { opt ->
            opt.longOpt.size() + (opt.hasArg() ? (opt.argName ?: argName).size() + 3 : 0)
        }.max()
    }
}
