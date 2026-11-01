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
//   read(byte[])       TaintSourceActualParam 0 -- the call FILLS the buffer
//   Props.load()       TaintSourceBase        -- the call fills its receiver
//   Conn.<init>(String) TaintSourceBase       -- the constructed object is a source
//   Request.validate() TaintSourceThis        -- `this` is tainted on entry
//   sinkBytes/sinkObj  TaintSinkActualParam 0
//   sinkBytesNeg/sinkObjNeg  sinks that must NEVER leak (negative controls)
//   fill(byte[])       not a source (negative control for read)
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

    // Actual-parameter source: read() fills its buffer argument.
    static int  read(byte[] buf) { buf[0] = 1; return 1; }
    static void fill(byte[] buf) { buf[0] = 2; }            // not a source

    // Base source: load() fills its receiver; Conn's constructor builds a
    // source object.
    static class Props { void load() { } }
    static class Conn  { Conn(String host) { } }

    // This source: validate() runs on an object built from untrusted data.
    static class Request {
        void validate() { sinkObj(this); }                   // LEAK  this source
    }

    static void sinkBytes(byte[] b)    { System.out.println(b.length); }
    static void sinkObj(Object o)      { System.out.println(o); }
    static void sinkByte(byte b)       { System.out.println(b); }
    static void sinkBytesNeg(byte[] b) { System.out.println(b.length); }
    static void sinkObjNeg(Object o)   { System.out.println(o); }

    public static void main(String[] args) {
        sink(untrusted());               // LEAK   return source -> sink
        sink(trusted());                 // no leak: not a source
        Reader r = new FileReader();
        sink(r.read());                  // LEAK   via override expansion
        handle(trusted());               // LEAK   inside handle: formal-param source
        sink2(untrusted(), trusted());   // no leak: taint at index 0, sink is index 1
        sink2(trusted(), untrusted());   // LEAK   taint at index 1
        sinkInt(untrustedInt());         // LEAK   primitive-typed (int) source

        byte[] buf = new byte[4];
        read(buf);                       // fills buf: actual-parameter source
        sinkBytes(buf);                  // LEAK   buf is tainted after read
        byte e = buf[0];
        sinkByte(e);                     // LEAK   element loaded from the filled buffer
        byte[] buf2 = new byte[4];
        fill(buf2);                      // not a source
        sinkBytesNeg(buf2);              // no leak

        Props p = new Props();
        p.load();                        // fills p: base source
        sinkObj(p);                      // LEAK
        Props q = new Props();           // never loaded
        sinkObjNeg(q);                   // no leak

        Conn c = new Conn("host");       // constructed object: base source on <init>
        sinkObj(c);                      // LEAK

        new Request().validate();        // LEAK inside validate (this source)
    }
}
