package org.clyze.doop.command

import groovy.transform.CompileStatic
import org.apache.commons.cli.Option

/**
 * An option that may mention a containing group.
 */
@CompileStatic
class GOption extends Option {
    String group

    GOption(String opt, String longOpt, boolean hasArg, String description, String group) {
        super(opt, longOpt, hasArg, description)
        this.group = group
    }
}

