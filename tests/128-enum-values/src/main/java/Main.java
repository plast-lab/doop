// Test 128: Enum.valueOf finds the constants through Class.getEnumConstantsShared,
// which calls the enum's values() reflectively. Color.values() is reachable;
// Shade's is not (no class object of Shade reaches getEnumConstantsShared).
public class Main {
    enum Color { RED, GREEN }
    enum Shade { DARK, LIGHT }

    public static void main(String[] args) {
        Color c = Color.valueOf(args.length > 0 ? args[0] : "RED");
        System.out.println(c.ordinal());
        System.out.println(Shade.DARK.name());
    }
}
