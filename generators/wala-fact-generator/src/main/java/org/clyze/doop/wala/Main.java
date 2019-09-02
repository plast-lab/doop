package org.clyze.doop.wala;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.python.PythonInvoker;
import org.clyze.doop.wala.WalaInvoker;

// Driver class to choose between Java and Python modes for WALA.
public class Main {
    public static void main(String[] args) throws IOException, DoopErrorCodeException {
        List<String> args0 = new LinkedList<>();
        boolean python = false;
        for (String arg : args)
            if (arg.equals("--python"))
                python = true;
            else
                args0.add(arg);
        if (python)
            (new PythonInvoker()).main(args0.toArray(new String[0]));
        else
            (new WalaInvoker()).main(args0.toArray(new String[0]));
    }
}
