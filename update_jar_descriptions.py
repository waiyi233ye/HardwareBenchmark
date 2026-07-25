#!/usr/bin/env python3
"""
Update description text in dist JAR metadata files IN-PLACE.
Preserves already-substituted Maven placeholders (version, api-version).
Only replaces the description wording.
"""
import zipfile
import shutil
import os
import re

DIST = "/workspace/dist"

# Old → New description mappings (old substring → new full line/value)
# We do targeted replacements to preserve all other fields.

OLD_DESC = "因租赁面板服一直卡顿、被卡得不耐烦而用 AI 编写的"
NEW_DESC = "因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写的"

# For mcmod.info (was "硬件检测与CPU跑分mod")
OLD_DESC_SHORT = "因租赁面板服一直卡顿、被卡得不耐烦而用 AI 编写的硬件检测与CPU跑分mod。"
NEW_DESC_SHORT_1710 = "因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写的硬件检测与跑分 mod：甜甜圈渲染、内存/磁盘测试、Linux 库自动补全。"
NEW_DESC_SHORT_1122 = "因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写的硬件检测与跑分 mod：甜甜圈渲染、内存/磁盘测试、Linux 库自动补全。"

# For mods.toml (was multi-line with "硬件检测与CPU跑分mod")
OLD_MODS_TOML_DESC = (
    "因租赁面板服一直卡顿、被卡得不耐烦而用 AI 编写的硬件检测与CPU跑分mod - 甜甜圈渲染跑分、内存/磁盘测试、Linux库自动补全。\n"
    "支持服务器锁定（跑分期间踢出玩家、阻止登录），保证跑分结果准确。"
)
NEW_MODS_TOML_DESC = (
    "因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写的硬件检测与跑分 mod：甜甜圈渲染跑分、内存/磁盘测试、Linux 库自动补全。\n"
    "跑分期间可锁定服务器（踢出玩家、阻止登录），保证结果准确。"
)

# Fabric mod.json
OLD_FABRIC_DESC = "因租赁面板服一直卡顿、被卡得不耐烦而用 AI 编写的硬件检测与跑分mod - 甜甜圈渲染跑分、内存/磁盘测试、Linux库自动补全。"
NEW_FABRIC_DESC = "因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写的硬件检测与跑分 mod：甜甜圈渲染跑分、内存/磁盘测试、Linux 库自动补全。"


def update_jar_content(jar_path, content_replacements):
    """Replace text content inside specific files in a JAR.
    content_replacements: { inner_path: [(old_text, new_text), ...] }
    """
    tmp_path = jar_path + ".tmp"
    updated = []
    with zipfile.ZipFile(jar_path, "r") as zin:
        with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename in content_replacements:
                    text = data.decode("utf-8", errors="replace")
                    for old_text, new_text in content_replacements[item.filename]:
                        if old_text in text:
                            text = text.replace(old_text, new_text)
                            updated.append(item.filename)
                    data = text.encode("utf-8")
                zout.writestr(item, data)
    shutil.move(tmp_path, jar_path)
    return updated


def main():
    jars = {
        "HardwareBenchmark-1.2.0-bukkit-java8.jar": {
            "plugin.yml": [(OLD_DESC, NEW_DESC)],
        },
        "HardwareBenchmark-1.2.0-bukkit-java17.jar": {
            "plugin.yml": [(OLD_DESC, NEW_DESC)],
        },
        "HardwareBenchmark-1.2.0-fabric-universal.jar": {
            "fabric.mod.json": [(OLD_FABRIC_DESC, NEW_FABRIC_DESC)],
        },
        "HardwareBenchmark-1.2.0-forge-1.7.10.jar": {
            "mcmod.info": [(OLD_DESC_SHORT, NEW_DESC_SHORT_1710)],
        },
        "HardwareBenchmark-1.2.0-forge-1.12.2.jar": {
            "mcmod.info": [(OLD_DESC_SHORT, NEW_DESC_SHORT_1122)],
        },
        "HardwareBenchmark-1.2.0-forge-1.16.5.jar": {
            "META-INF/mods.toml": [(OLD_MODS_TOML_DESC, NEW_MODS_TOML_DESC)],
        },
        "HardwareBenchmark-1.2.0-forge-1.18plus.jar": {
            "META-INF/mods.toml": [(OLD_MODS_TOML_DESC, NEW_MODS_TOML_DESC)],
        },
    }

    print("=== 更新 dist JAR 描述文本（就地替换，保留已替换的 Maven 变量）===")
    for jar_name, replacements in jars.items():
        jar_path = os.path.join(DIST, jar_name)
        if not os.path.exists(jar_path):
            print(f"[SKIP] {jar_name} 不存在")
            continue
        updated = update_jar_content(jar_path, replacements)
        size = os.path.getsize(jar_path)
        if updated:
            print(f"[OK] {jar_name} ({size:,} bytes) - 已更新: {', '.join(set(updated))}")
        else:
            print(f"[WARN] {jar_name} ({size:,} bytes) - 未找到旧描述文本，可能已更新")

    # Verify
    print("\n=== 验证更新结果 ===")
    for jar_name in jars:
        jar_path = os.path.join(DIST, jar_name)
        if not os.path.exists(jar_path):
            continue
        inner_path = list(jars[jar_name].keys())[0]
        with zipfile.ZipFile(jar_path, "r") as z:
            content = z.read(inner_path).decode("utf-8", errors="replace")
            for line in content.split("\n"):
                if "因租用" in line or "description" in line.lower():
                    if "因租用" in line:
                        print(f"  {jar_name}:{inner_path}")
                        print(f"    {line.strip()[:150]}")
                        break


if __name__ == "__main__":
    main()
