package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.DataBase;
import org.clyze.doop.ptatoolkit.doop.Query;
import org.clyze.doop.ptatoolkit.doop.basic.DoopInstanceCallSite;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;
import org.clyze.doop.ptatoolkit.pta.basic.InstanceCallSite;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InstanceCallSiteFactory extends ElementFactory<InstanceCallSite> {

    private final Map<String, Variable> call2recv = new HashMap<>();
    private final Map<String, Set<Variable>> call2args = new HashMap<>();

    public InstanceCallSiteFactory(DataBase db, VariableFactory varFactory) {
        db.query(Query.INST_CALL_RECV).forEachRemaining(list -> {
            String call = list.get(0);
            Variable thisVar = varFactory.get(list.get(1));
            call2recv.put(call, thisVar);
        });

        db.query(Query.INST_CALL_ARGS).forEachRemaining(list -> {
            String call = list.get(0);
            Variable arg = varFactory.get(list.get(1));
            if (!call2args.containsKey(call)) {
                call2args.put(call, new HashSet<>(4));
            }
            call2args.get(call).add(arg);
        });
    }

    @Override
    protected InstanceCallSite createElement(String callSite) {
        Variable recv = call2recv.get(callSite);
        Set<Variable> args = call2args.get(callSite);
        if (args == null) {
            args = Collections.emptySet();
        }
        return new DoopInstanceCallSite(callSite, recv, args, ++count);
    }

}
