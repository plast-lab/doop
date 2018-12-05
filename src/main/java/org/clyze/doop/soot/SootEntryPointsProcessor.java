package org.clyze.doop.soot;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.EntryPointsProcessor;

class SootEntryPointsProcessor extends EntryPointsProcessor {
    /*
     * Doop method ids must escape Jimple keywords when Soot is used.
     */
    @Override
    protected void writeAndroidKeepMethodDoopId(Database db, String doopId) {
        int firstSpace = doopId.indexOf(' ', 0);
        if (firstSpace != -1) {
            int secondSpace = doopId.indexOf(' ', firstSpace + 1);
            if (secondSpace != -1) {
                int lParen = doopId.indexOf('(', secondSpace + 1);
                if (lParen != -1) {
                    String name = doopId.substring(secondSpace + 1, lParen);
                    String escaped = Representation.escapeSimpleName(name);
                    doopId = doopId.substring(0, secondSpace + 1) + escaped + doopId.substring(lParen, doopId.length());
                }
            }
        }
        super.writeAndroidKeepMethodDoopId(db, doopId);
    }
}
