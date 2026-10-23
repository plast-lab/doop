// Driver for the P/Taint source vocabulary
// (souffle-logic/addons/information-flow, spec in addons/testing/TestTaintSources.dl).
//
// Every source and sink is one of the driver's OWN methods, so the spec can name
// them exactly and the expected leaks are known per call site. The test logic
// anchors on the producing/consuming method of each sink argument, never on
// invocation numbering.
//
//   untrusted()        TaintSourceReturn      -- its result is tainted
//   Reader.read()      TaintSourceReturn      -- declared on the INTERFACE; the call
//                                                resolves to FileReader.read, so the
//                                                leak depends on override expansion
//   handle(String)     TaintSourceFormalParam -- its formal is tainted on entry
//   untrustedInt()     TaintSourceReturn      -- a primitive-typed (int) source
//   sink(String)       TaintSinkActualParam 0
//   sink2(String,String) TaintSinkActualParam 1 (index 0 is NOT a sink)
//   sinkInt(int)       TaintSinkActualParam 0
//   trusted()          not a source (negative control)
public class Main {

    interface Reader { String read(); }
    static class FileReader implements Reader {
        public String read() { return "file"; }
    }

    static String untrusted()    { return "u"; }
    static String trusted()      { return "t"; }
    static int    untrustedInt() { return 7; }

    static void sink(String s)            { System.out.println(s); }
    static void sink2(String a, String b) { System.out.println(a); System.out.println(b); }
    static void sinkInt(int n)            { System.out.println(n); }

    // Formal-parameter source: 'req' is tainted on entry regardless of what the
    // caller passes (main passes a trusted value).
    static void handle(String req) { sink(req); }

    public static void main(String[] args) {
        sink(untrusted());               // LEAK   return source -> sink
        sink(trusted());                 // no leak: not a source
        Reader r = new FileReader();
        sink(r.read());                  // LEAK   via override expansion
        handle(trusted());               // LEAK   inside handle: formal-param source
        sink2(untrusted(), trusted());   // no leak: taint at index 0, sink is index 1
        sink2(trusted(), untrusted());   // LEAK   taint at index 1
        sinkInt(untrustedInt());         // LEAK   primitive-typed (int) source
    }
}
