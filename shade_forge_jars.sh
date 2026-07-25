#!/bin/bash
# 手动将依赖 shade 进 Forge 1.18+ jar
# 解决 ForgeGradle 无法自动构建的问题

set -e

M2=/root/.m2/repository
DIST=/workspace/dist
WORK=/tmp/forge-shade

DEPS=(
    "$M2/com/github/oshi/oshi-core/6.6.5/oshi-core-6.6.5.jar"
    "$M2/net/java/dev/jna/jna/5.15.0/jna-5.15.0.jar"
    "$M2/net/java/dev/jna/jna-platform/5.15.0/jna-platform-5.15.0.jar"
    "$M2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"
    "$M2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"
    "/workspace/common/target/HardwareBenchmark-common-1.2.0.jar"
)

for VERSION in 1.18.2 1.19.2 1.20.1; do
    echo "=== Shading Forge $VERSION ==="
    SRC_JAR="$DIST/HardwareBenchmark-1.2.0-forge-$VERSION.jar"
    if [ ! -f "$SRC_JAR" ]; then
        echo "  Source jar not found: $SRC_JAR"
        continue
    fi

    rm -rf "$WORK"
    mkdir -p "$WORK/mod" "$WORK/deps"
    cd "$WORK"

    # 1. Unpack the original mod jar
    unzip -q -o "$SRC_JAR" -d mod/

    # 2. Unpack all dependency jars into deps/
    for dep in "${DEPS[@]}"; do
        if [ -f "$dep" ]; then
            echo "  Adding: $(basename $dep)"
            unzip -q -o "$dep" -d deps/ || true
        else
            echo "  WARNING: dep not found: $dep"
        fi
    done

    # 3. Copy ALL dependency files into mod/ using rsync-like approach with cp -r
    #    Use cp -r to copy the whole deps/ tree into mod/, then clean up
    cp -rf deps/* mod/ 2>/dev/null || true
    # Also copy any hidden files (just in case)
    cp -rf deps/.* mod/ 2>/dev/null || true

    # 4. Remove unwanted files from mod/
    #    - module-info.class (causes JPMS conflicts)
    #    - META-INF signatures (*.SF, *.DSA, *.RSA)
    #    - Maven metadata from deps (keep mod's own)
    find mod/ -name "module-info.class" -delete
    find mod/META-INF -name "*.SF" -delete 2>/dev/null || true
    find mod/META-INF -name "*.DSA" -delete 2>/dev/null || true
    find mod/META-INF -name "*.RSA" -delete 2>/dev/null || true
    # Remove dependency MANIFEST.MF but keep mod's original (it has Forge metadata)
    # Actually, we need a valid MANIFEST.MF - regenerate a minimal one
    cat > mod/META-INF/MANIFEST.MF << 'EOF'
Manifest-Version: 1.0
EOF

    # 5. Add pack.mcmeta (required by Forge 1.18+)
    cat > mod/pack.mcmeta << 'EOF'
{
  "pack": {
    "description": "HardwareBenchmark resources",
    "pack_format": 15
  }
}
EOF

    # 6. Repackage the jar from scratch
    rm -f "$SRC_JAR"
    cd mod
    zip -q -r "$SRC_JAR" . -x "*.DS_Store"
    cd "$WORK"

    FINAL_SIZE=$(ls -la "$SRC_JAR" | awk '{print $5}')
    FILE_COUNT=$(find mod/ -type f | wc -l)
    echo "  Done: $FINAL_SIZE bytes, $FILE_COUNT files"
done

echo "=== Shading complete ==="
