#!/usr/bin/env bash
# Rebuild the input-flags test resource jars from source.
# Java 17 bytecode; the tests use --platform java_17, which Doop constructs
# locally from the running JDK 17 (no network). Requires JDK 17. Run from
# anywhere; paths are resolved relative to this file.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"     # .../input-flags/src
OUT="$(dirname "$HERE")"                   # .../input-flags  (jars go here)
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- duplicate-class inputs -------------------------------------------------
javac --release 17 -d "$TMP/v1"   "$HERE/dup/v1/com/foo/Bar.java"
javac --release 17 -d "$TMP/v2"   "$HERE/dup/v2/com/foo/Bar.java"
javac --release 17 -cp "$TMP/v1" -d "$TMP/main" "$HERE/dup/main/Main.java"   # Main not bundling Bar
jar cf  "$OUT/barV1.jar" -C "$TMP/v1" .
jar cf  "$OUT/barV2.jar" -C "$TMP/v2" .
jar cfe "$OUT/main.jar" Main -C "$TMP/main" .

# --- resolution-depth inputs ------------------------------------------------
javac --release 17 -d "$TMP/lib" \
      "$HERE/depth/lib/com/foo/Deep.java" "$HERE/depth/lib/com/foo/Baz.java" "$HERE/depth/lib/com/foo/Refl.java"
javac --release 17 -cp "$TMP/lib" -d "$TMP/main-type" "$HERE/depth/app-type/Main.java"
javac --release 17 -d "$TMP/main-reflect" "$HERE/depth/app-reflect/Main.java"   # no Refl on cp: pure string ref
jar cf  "$OUT/libdeep.jar"      -C "$TMP/lib" .
jar cfe "$OUT/main-type.jar"    Main -C "$TMP/main-type" .
jar cfe "$OUT/main-reflect.jar" Main -C "$TMP/main-reflect" .

echo "built: $(cd "$OUT" && ls *.jar | tr '\n' ' ')"
