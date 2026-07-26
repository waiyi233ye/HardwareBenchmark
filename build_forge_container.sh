#!/bin/bash
# Build the Forge universal container JAR (forge-container).
# Compiles with Java 8 against stub annotation types, then stages and packages.
set -e

cd /workspace/forge-container

JAVA8=/root/.local/share/mise/installs/java/temurin-8.0.482+8/bin/java
JAVAC8=/root/.local/share/mise/installs/java/temurin-8.0.482+8/bin/javac
JAR8=/root/.local/share/mise/installs/java/temurin-8.0.482+8/bin/jar

# Stub classpath: Forge (net.minecraftforge) + NeoForge (net.neoforged) + cpw stubs
STUB_CP="build/stub-classes:build/cpw-only"

echo "=== Step 1: Compile Java sources (Java 8, with stub classpath) ==="
mkdir -p build/classes
"$JAVAC8" -source 8 -target 8 -cp "$STUB_CP" \
    -d build/classes \
    src/main/java/com/hwbench/forge/container/*.java
echo "  Compiled $(find build/classes -name '*.class' | wc -l) class files"

echo "=== Step 2: Build stage directory ==="
rm -rf build/stage
mkdir -p build/stage/META-INF/jars
mkdir -p build/stage/com/hwbench/forge/container

# Copy compiled classes
cp -r build/classes/com/hwbench/forge/container/*.class build/stage/com/hwbench/forge/container/

# Copy META-INF (manifest, mods.toml, neoforge.mods.toml)
cp src/main/resources/META-INF/MANIFEST.MF build/stage/META-INF/
cp src/main/resources/META-INF/mods.toml build/stage/META-INF/
cp src/main/resources/META-INF/neoforge.mods.toml build/stage/META-INF/

# Copy mcmod.info (Forge 1.7.10/1.12.2 metadata)
cp src/main/resources/mcmod.info build/stage/

# Copy embedded sub-JARs (4 era-specific Forge sub-JARs)
cp build/jars/forge-1.7.10.jar build/stage/META-INF/jars/
cp build/jars/forge-1.12.2.jar build/stage/META-INF/jars/
cp build/jars/forge-1.16.5.jar build/stage/META-INF/jars/
cp build/jars/forge-1.18plus.jar build/stage/META-INF/jars/

echo "  Stage contents:"
find build/stage -type f | sort

echo "=== Step 3: Package container JAR ==="
DIST=/workspace/dist
mkdir -p "$DIST"
OUT_JAR="$DIST/HardwareBenchmark-2.0.0-forge-universal.jar"
rm -f "$OUT_JAR"
cd build/stage
"$JAR8" cfm "$OUT_JAR" META-INF/MANIFEST.MF \
    -C . com \
    -C . META-INF \
    -C . mcmod.info

echo "=== Build complete ==="
ls -la "$OUT_JAR"
echo "  JAR contents:"
"$JAR8" tf "$OUT_JAR" | head -25
