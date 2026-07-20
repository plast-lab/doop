// Driver for the primitive-value modeling logic
// (souffle-logic/main/model-primitives.dl).
//
// Each construct is annotated with the seeding rule it targets and the result
// relation that should witness the resulting <prim-*> mock object after a
// context-insensitive analysis run with --enable-model-primitives. The mock
// object is typed as the boxed wrapper of the primitive (int ->
// java.lang.Integer, etc.).
//
//   (1) primitive formal parameters      -> <prim-param ...>
//   (2) arithmetic results (binop/unop)  -> <prim-arith ...>
//   (3) numeric constants (per literal)  -> <prim-const ...>
//
// IMPORTANT -- the driver is written to defeat two optimizations that would
// otherwise erase the very instructions the rules key on:
//   * javac constant folding: an arithmetic expression whose operands are all
//     compile-time constants (e.g. `42L + 42L`) is folded to a single constant,
//     leaving no binop. Every arithmetic expression here therefore involves a
//     non-constant parameter ('factor' / 'seed').
//   * Soot local aggregation: a constant assigned to a local used exactly once
//     is inlined into that use, erasing the numeric-constant assignment. Each
//     constant local 'k' below is used TWICE so it survives as a real
//     AssignNumConstant instruction.
public class Main {

    static int  sField;   // static primitive field   (witness: StaticFieldPointsTo)
    long        iField;   // instance primitive field  (witness: InstanceFieldPointsTo)

    // (1) int param 'factor'; (3) int const 'k' (used twice); (2) int binops.
    static int scale(int factor) {
        int k = 5;
        return factor * k + k;
    }

    // (1) long param 'seed'; (3) long const; (2) long binops.
    static long makeLong(long seed) {
        long k = 42L;
        return seed * k + k;
    }

    // (1) double param 'seed'; (3) double const; (2) double binops.
    static double makeDouble(double seed) {
        double k = 3.14;
        return seed * k + k;
    }

    // (1) boolean param 'flag'.
    static boolean invert(boolean flag) {
        return !flag;
    }

    // (3) numeric constant that SURVIVES Soot's constant propagation. A
    // straight-line `int k = 5` gets folded into its uses and deleted; but the
    // loop-header merges below make 'sum' and 'i' two-valued (0 vs. the updated
    // value), so `sum = 0` / `i = 0` cannot be propagated to a single constant
    // and survive as real AssignNumConstant instructions -> <prim-const ...>.
    static int loopSum(int bound) {
        int sum = 0;
        for (int i = 0; i < bound; i++) {
            sum = sum + i;
        }
        return sum;
    }

    public static void main(String[] args) {
        Main m = new Main();
        int n = args.length;          // non-constant seed source

        int a = scale(n);
        sField = a;                   // static field store (int): from scale's return

        long l = makeLong((long) n);
        m.iField = l;                 // instance field store (long): from makeLong's return

        boolean bo = invert(args.length > 0);
        double dd = makeDouble((double) n);
        int s = loopSum(n);           // exercises rule (3): a surviving int constant

        // Full primitive-type coverage: casts of a variable produce <prim-conv>
        // objects (rule 5) for the remaining wrapper types (Float/Short/Byte/
        // Character). Kept live via a concatenation so the casts survive.
        float fl = (float) n;
        short sh = (short) n;
        byte  by = (byte)  n;
        char  ch = (char)  n;
        System.out.println("" + fl + sh + by + ch);

        // Primitive array-element store + LOAD round-trip. The stock array-store
        // rule type-filters the wrapper-typed prim object out (component type int
        // is not a supertype of java.lang.Integer); model-primitives rule (6)
        // restores it, so the object reaches ArrayIndexPointsTo (ArrayStoreMissing)
        // and is read back by the load into y (ArrayLoadMissing).
        int[] arr = new int[8];
        arr[0] = a;
        int y = arr[0];
        System.out.println(y);
    }
}
