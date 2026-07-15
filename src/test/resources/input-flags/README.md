# input-flags test resources

Prebuilt input jars for `TestInputFlags` (characterization of `-i`/`-l`
semantics). Committed as binaries (Java 17 bytecode) so the test can drive exact
`-i`/`-l` orderings and duplicate class names — which the standard one-jar test
subprojects can't express. The test uses `--platform java_17` (constructed by
Doop from the running JDK, no network). Regenerate with `src/build.sh` (JDK 17).

| Jar | Contents | Role |
|-----|----------|------|
| `barV1.jar` / `barV2.jar` | `com.foo.Bar` v1 / v2 (`markerV1`/`markerV2`, `whoAmI`) | duplicate class name, two versions |
| `main.jar` | `Main` doing `new com.foo.Bar().whoAmI()` | references Bar (dup + app-scope cases) |
| `libdeep.jar` | `com.foo.{Baz,Deep,Refl}` | library closure for the depth cases |
| `main-type.jar` | `Main` referencing `Baz` only as a field/param TYPE | type-only reference probe |
| `main-reflect.jar` | `Main` doing `Class.forName("com.foo.Refl").newInstance()...` | reflection-only reference probe |

## Sources (`src/`, built by `src/build.sh`)

Every class carries **version/identity markers** so the test can tell — by
grepping the generated `*.facts` — *which* body Soot loaded and *to what depth*:

- a distinctly-named **method** (`markerV1`, `markerV2`, `deepMarker`, …) →
  appears in `Method.facts` once the class is resolved to signatures;
- a distinctly-named **field** (`fieldV1`, `fieldV2`, `bazField`) → appears in
  `Field.facts` at the same level;
- a **body-only string constant** returned by `whoAmI()` (`"Bar-V1"`,
  `"Baz-BODY"`, …) → appears in `StringConstant.facts` only if the method
  **body** was generated (i.e. resolved to `BODIES`, not just signatures).

### Duplicate-class inputs (`src/dup/`)

- **`v1/com/foo/Bar.java`** — version 1 of `com.foo.Bar`: field `fieldV1`,
  `markerV1()`, and `whoAmI()` returning `"Bar-V1"`. Compiled into `barV1.jar`.
- **`v2/com/foo/Bar.java`** — version 2 of the *same* class name `com.foo.Bar`,
  with the V2 markers (`fieldV2`, `markerV2()`, `"Bar-V2"`). Compiled into
  `barV2.jar`. Two jars declaring the same FQN is what lets the test observe
  which one wins under different `-i`/`-l` orderings.
- **`main/Main.java`** — entry point that does `new com.foo.Bar().whoAmI()`,
  binding to `Bar` through members common to both versions. Its allocation +
  call is what makes `Bar` reachable/loaded regardless of which jar supplies it.
  Compiled (against V1, but **not** bundling `Bar`) into `main.jar`.

### Resolution-depth inputs (`src/depth/`)

- **`lib/com/foo/Deep.java`** — third-level library class (`deepMarker()`,
  `whoAmI()` → `"Deep-BODY"`). Referenced only from `Baz`'s body; its presence
  in the facts proves Soot followed `Baz`'s body references (closure depth).
- **`lib/com/foo/Baz.java`** — library class with `bazField`, `whoAmI()` →
  `"Baz-BODY"`, and `touchDeep()`, whose **body** does `new com.foo.Deep()` /
  `deepMarker()`. If `Baz` is resolved to bodies, that reference pulls in `Deep`.
- **`lib/com/foo/Refl.java`** — library class (`reflMarker()`, `whoAmI()` →
  `"Refl-BODY"`) referenced *only* reflectively. All three compile into
  `libdeep.jar`.
- **`app-type/Main.java`** — references `com.foo.Baz` **only as a type**: a
  `static Baz bazField` and a `takesBaz(Baz)` parameter, with **no** allocation
  or invocation (`main` is empty). Probes what level a purely type-referenced
  `-l` class reaches. Compiled into `main-type.jar`.
- **`app-reflect/Main.java`** — references `com.foo.Refl` **only via a string**:
  `Class.forName("com.foo.Refl")`, then `getDeclaredConstructor().newInstance()`
  and a reflective `reflMarker` invoke. The name appears solely as a
  `StringConstant` (no `CONSTANT_Class_info`), so it probes whether reflective
  use pulls the class into the facts. Compiled into `main-reflect.jar`.

