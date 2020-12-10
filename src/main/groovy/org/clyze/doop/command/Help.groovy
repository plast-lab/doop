package org.clyze.doop.command

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor
import groovy.transform.CompileStatic
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

/**
 * A small help system for navigating Doop options.
 */
@CompileStatic
class Help {

    /** The "group" of options not belonging to groups. */
    private static final String GENERAL_GROUP = 'general'

    /** The "group" of all options. */
    private static final String GROUP_ALL = 'all'

    /**
     * Main method to display help text. Depending on the command-line
     * arguments, it may print a short help text, a particular group of
     * options or all options.
     * @param cli           the CLI object (may be null)
     * @param builder       the CLI builder object
     */
    static void usage(OptionAccessor cli, CliBuilder builder) {
        Map<String, String> groupMap = genIdToGroup(builder)

        // Update "valid values" in "help" command-line option.
        Option updatedHelp = builder.options.getOption('help')
        updatedHelp.description = CommandLineAnalysisFactory.validValues(updatedHelp.description, groupMap.keySet())

        if (cli == null || cli['h'] == null || cli['h'] == true) {
            showBasicUsage(builder)
        } else if (cli['h'] == GROUP_ALL) {
            // Show detailed help for all options.
            builder.usage()
        } else if (cli['h'] == GENERAL_GROUP) {
            // Show detailed help for options that do not belong to special groups.
            Collection<Option> opts = builder.options.options.findAll { !(it instanceof GOption) || ((it as GOption).group == null) }
            printGroup(HelpGroupFormatter.GENERAL_OPTIONS, opts)
        } else {
            // Show help for specific options group.
            String groupId = cli['h']
            String group = groupMap.get(groupId)
            if (group == null)
                println "WARNING: section ${groupId} does not exist."
            else {
                Collection<Option> opts = builder.options.options.findAll { (it instanceof GOption) && ((it as GOption).group == group) }
                printGroup(group, opts)
            }
        }
        showHelpFooter(groupMap)
    }

    static void printGroup(String group, Collection<Option> optCollection) {
        Options groupOptions = new Options()
        for (Option opt : optCollection)
            groupOptions.addOption(opt)
        HelpGroupFormatter formatter = new HelpGroupFormatter()
        formatter.width = CommandLineAnalysisFactory.WIDTH
        PrintWriter pw = new PrintWriter(System.out)
        Tuple2<Options, Integer> optsInfo = HelpGroupFormatter.getOptionsInfo(groupOptions)
        formatter.printGroup(pw, group, CommandLineAnalysisFactory.WIDTH, optsInfo.v2, optsInfo, 0, 3)
        pw.flush()
    }

    static List<String> getGroups(Map<String, ?> groupMap) {
        return [GENERAL_GROUP] + groupMap.keySet().toList() + [GROUP_ALL]
    }

    static String textToId(String s) {
        StringBuilder sb = new StringBuilder()
        for (char c : s.toCharArray())
            if (c != '[' && c != ']')
                sb.append(Character.isLetter(c) ? Character.toLowerCase(c) : '-')
        return sb.toString()
    }

    static Map<String, String> genIdToGroup(CliBuilder builder) {
        Collection<Option> options = builder.options.options as Collection<Option>
        Map<String, String> map = new HashMap<>()
        for (Option opt : options) {
            if (opt instanceof GOption) {
                GOption gopt = opt as GOption
                String group = gopt.group
                if (group != null)
                    map.put(textToId(group), group)
            }
        }
        return map
    }

    static void showHelpFooter(Map<String, ?> groupMap) {
        List<String> groups = getGroups(groupMap)
        println()
        println "Use --help <SECTION> for more information, available sections: " + String.join(', ', groups)
    }

    private static void showBasicUsage(CliBuilder builder) {
        Set<String> basicOptions = new HashSet<>()
        basicOptions.addAll(['analysis', 'input-file', 'library-file', 'platform', 'id'])
        List<Option> optList = (builder.options.options as Collection<Option>)
                .findAll { Option opt -> basicOptions.contains(opt.longOpt) }
                .collect { Option opt -> simplifyBasicOption(opt) } as List<Option>

        println 'usage: ' + CommandLineAnalysisFactory.USAGE
        println ''
        println 'Run an analysis on a program (given as a combinvation of code inputs, code libraries, and a platform).'
        println ''
        printGroup('Basic options', optList)
    }

    static Option simplifyBasicOption(Option opt) {
        if (opt.longOpt == 'analysis')
            opt.description = 'The analysis to use. Examples: context-insensitive, 1-call-site-sensitive, micro'
        else if (opt.longOpt == 'platform')
            opt.description = 'The platform on which to perform the analysis. Examples: java_8, android_25_fulljars, python_2'
        return opt
    }
}
