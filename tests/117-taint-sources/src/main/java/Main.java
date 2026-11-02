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
//   Main.CONFIG        TaintSourceField       -- static field, never assigned
//   Settings.name      TaintSourceField       -- instance field, assigned in the ctor
//   Settings.external  TaintSourceField       -- instance field, never assigned
//   Settings.data      TaintSourceField       -- holds an app allocation: the
//                                                array itself must be marked
//   Settings.safe      not a source (negative control for fields)
//   Token              TaintSourceType        -- every Token is tainted, wherever
//                                                allocated; AdminToken is a subtype
//   Ticket             not a source (negative control for types)
//   Session            TaintSourceType        -- NEVER allocated: only the mock half
//                                                (stand-ins at its entry positions:
//                                                serve's formal, field CURRENT, the
//                                                result of native openSession())
//                                                can make a Session reach a sink
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

    // Field sources: the field's contents are tainted, whether or not the
    // program ever stores into the field.
    static String CONFIG;                                   // never assigned
    static class Settings {
        String name;                                        // assigned in the constructor
        String external;                                    // never assigned anywhere
        byte[] data;                                        // assigned an app allocation
        String safe = "ok";                                 // not a source
        Settings(String n) { name = n; }
    }

    // Type sources: every object of the type (or a subtype) is tainted.
    static class Token { }
    static class AdminToken extends Token { }
    static class Ticket { }                                 // not a source

    // Type source, mock half: the program never allocates a Session, so every
    // Session reaching a sink is a stand-in placed at an entry position of the type.
    static class Session { }
    static Session CURRENT;                                 // field declared as Session, never assigned
    static native Session openSession();                    // body-less: its result comes from outside
    static void serve(Session s) { sinkObj(s); }            // LEAK inside serve: formal declared as Session

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

        sink(CONFIG);                    // LEAK   static field source (no store exists)
        Settings st = new Settings("n");
        sink(st.name);                   // LEAK   field source with a constructor store
        sink(st.external);               // LEAK   field source with no store
        sink(st.safe);                   // no leak: not a source
        byte[] data = new byte[2];
        st.data = data;
        sinkBytes(data);                 // LEAK   the array is MARKED: this alias never
                                         //        goes through the field

        sinkObj(new Token());            // LEAK   type source
        sinkObj(new AdminToken());       // LEAK   subtype of a type source
        sinkObjNeg(new Ticket());        // no leak: not a source

        serve(null);                     // LEAK inside serve: formal entry position
        sinkObj(CURRENT);                // LEAK   field entry position (no store exists)
        sinkObj(openSession());          // LEAK   body-less method result entry position
    }
}
