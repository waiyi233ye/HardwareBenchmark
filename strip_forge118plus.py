#!/usr/bin/env python3
"""Strip shaded library packages that conflict with Forge 1.18+'s bundled modules.

Forge 1.18+ uses the Java module system (JPMS). Our shaded JAR contains
oshi/, org/slf4j/, com/sun/jna/ which are ALSO bundled by Forge, causing
split-package ResolutionException at startup.

Fix: remove these packages from our JAR. Forge's bundled versions will be
used instead. Our mod code (HWBenchForge.java) imports org.slf4j.Logger
which will resolve to Forge's slf4j at runtime.
"""
import zipfile
import shutil
import os
import sys

JAR = "/workspace/dist/HardwareBenchmark-1.2.0-forge-1.18plus.jar"

# Packages/dirs to strip (conflict with Forge bundled libs)
STRIP_PREFIXES = (
    "oshi/",
    "org/slf4j/",
    "com/sun/jna/",
    "com/google/gson/",
    "META-INF/versions/9/module-info.class",
    "module-info.class",
)

# Also strip individual conflicting files
STRIP_EXACT = {
    "module-info.class",
    "META-INF/versions/9/module-info.class",
}

tmp = JAR + ".tmp"
stripped = []
kept = 0
with zipfile.ZipFile(JAR, "r") as zin:
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            name = item.filename
            strip = False
            if name in STRIP_EXACT:
                strip = True
            else:
                for p in STRIP_PREFIXES:
                    if name.startswith(p):
                        strip = True
                        break
            if strip:
                stripped.append(name)
            else:
                zout.writestr(item, zin.read(name))
                kept += 1

shutil.move(tmp, JAR)
print(f"Stripped {len(stripped)} entries, kept {kept}")
print(f"Output: {JAR} ({os.path.getsize(JAR)} bytes)")
if stripped:
    # Show summary by prefix
    from collections import Counter
    prefix_counts = Counter()
    for s in stripped:
        for p in STRIP_PREFIXES:
            if s.startswith(p) or s == p:
                prefix_counts[p] += 1
                break
    for p, c in sorted(prefix_counts.items()):
        print(f"  {p}: {c} entries")
