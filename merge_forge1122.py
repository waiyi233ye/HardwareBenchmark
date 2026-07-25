#!/usr/bin/env python3
"""Merge correctly-reobfuscated com/hwbench/forge/ classes from Gradle build
into the shaded dist JAR, and strip module-info.class entries."""
import zipfile
import shutil
import os
import sys

DIST_JAR = "/workspace/dist/HardwareBenchmark-1.2.0-forge-1.12.2.jar"
GRADLE_JAR = "/workspace/forge-1.12.2/build/libs/HardwareBenchmark-forge-1.12.2-1.2.0.jar"
OUTPUT_JAR = DIST_JAR  # in-place

# Read gradle classes (correctly reobfuscated)
gradle_classes = {}
with zipfile.ZipFile(GRADLE_JAR, "r") as zf:
    for name in zf.namelist():
        if name.startswith("com/hwbench/forge/") and name.endswith(".class"):
            gradle_classes[name] = zf.read(name)
            print(f"  gradle: {name} ({len(gradle_classes[name])} bytes)")

print(f"Loaded {len(gradle_classes)} gradle-reobf forge classes")

# Merge into dist JAR
tmp = DIST_JAR + ".tmp"
stripped = 0
replaced = 0
copied = 0
with zipfile.ZipFile(DIST_JAR, "r") as zin:
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            name = item.filename
            # Strip module-info.class (Forge 1.12.2 ASM 5.2 can't read it)
            if name == "module-info.class" or (
                name.startswith("META-INF/versions/") and name.endswith("module-info.class")
            ):
                stripped += 1
                print(f"  strip: {name}")
                continue
            # Replace forge classes with gradle-reobf versions
            if name in gradle_classes:
                data = gradle_classes[name]
                replaced += 1
                print(f"  replace: {name} ({len(data)} bytes)")
            else:
                data = zin.read(name)
                copied += 1
            zout.writestr(item, data)

shutil.move(tmp, OUTPUT_JAR)
print(f"\nDone: {replaced} replaced, {copied} copied, {stripped} stripped")
print(f"Output: {OUTPUT_JAR} ({os.path.getsize(OUTPUT_JAR)} bytes)")
