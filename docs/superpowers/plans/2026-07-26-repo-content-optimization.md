# HardwareBenchmark 仓库内容优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 main 分支残留的 7 个开发产物、修复 StandaloneDemo 泄漏进生产 JAR、对齐 Issue 模板/README/TEST_REPORT/fabric.mod.json 与 v2.0.0、使构建脚本可移植化，直推 4 个分类 commit 到 `origin/main`。

**Architecture:** 4 个独立分类 commit（chore/fix/docs/build），每个 commit 自包含、可独立 revert，不升版本号、不重打包 JAR、不发新 Release。提交通过本地 git 完成，推送后用 GitHub MCP 工具验证。

**Tech Stack:** Bash（构建脚本）、Maven POM XML（编译排除）、Markdown（文档）、JSON（Fabric 元数据）、Git CLI（提交与推送）、GitHub MCP tools（验证）。

**Spec:** `docs/superpowers/specs/2026-07-26-repo-content-optimization-design.md`

---

## File Structure

修改/删除的文件按 commit 分组：

**Commit 1 (chore: remove dev artifacts)**
- Delete: `ReobfJar.java`
- Delete: `merge_forge1122.py`
- Delete: `strip_forge118plus.py`
- Delete: `update_jar_descriptions.py`
- Delete: `update_jar_plugin.py`
- Delete: `test-lightweight-config.yml`
- Delete: `release-notes-v1.2.1.md`
- Modify: `.gitignore`（新增 5 个忽略规则）

**Commit 2 (fix: exclude StandaloneDemo)**
- Modify: `common/pom.xml`（添加 `<excludes>` 配置）

**Commit 3 (docs: align with v2.0.0)**
- Modify: `.github/ISSUE_TEMPLATE/bug_report.md`（更新示例值）
- Modify: `fabric/src/main/resources/fabric.mod.json`（`java>=17` → `java>=8`）
- Modify: `README.md`（Fabric "全部" → "全部版本"）
- Modify: `docs/TEST_REPORT.md`（证据索引去硬编码路径）

**Commit 4 (build: portable script + clean gitignore)**
- Modify: `build_forge_container.sh`（去硬编码路径，版本号动态读取）
- Modify: `.gitignore`（移除 `*.jar` + `!dist/*.jar` 死规则）

---

### Task 1: Commit 1 — 删除 7 个 dev artifacts 并更新 .gitignore

**Files:**
- Delete: `ReobfJar.java`, `merge_forge1122.py`, `strip_forge118plus.py`, `update_jar_descriptions.py`, `update_jar_plugin.py`, `test-lightweight-config.yml`, `release-notes-v1.2.1.md`
- Modify: `.gitignore`（在 "v2.0.0 开发工具与部署脚本" 区块追加 5 行）

- [ ] **Step 1: 删除 7 个 dev artifacts**

```bash
cd /workspace
git rm ReobfJar.java merge_forge1122.py strip_forge118plus.py update_jar_descriptions.py update_jar_plugin.py test-lightweight-config.yml release-notes-v1.2.1.md
```

Expected output: 7 行 `rm 'filename'`

- [ ] **Step 2: 更新 `.gitignore` 追加 5 个忽略规则**

在 `.gitignore` 的 "v2.0.0 开发工具与部署脚本（不入库）" 区块（第 75-83 行附近），在 `strip_forge_1.18plus_deps.py` 行之后追加以下 5 行：

```diff
 # v2.0.0 开发工具与部署脚本（不入库）
 __pycache__/
 deploy_jars.py
 deploy_universal_jars.py
 fix_fabric_inner_jar.py
 patch_jar_version.py
 patch_jars_to_1.2.1.py
 retest_forge_1.18plus.py
 strip_forge_1.18plus_deps.py
+ReobfJar.java
+merge_forge1122.py
+strip_forge118plus.py
+update_jar_descriptions.py
+update_jar_plugin.py
+test-lightweight-config.yml
```

精确编辑（在 `strip_forge_1.18plus_deps.py` 这一行后追加）：

使用 Edit 工具，`old_string`:
```
strip_forge_1.18plus_deps.py

# Release Notes 文档（内容放在 GitHub Release body 里，不入库）
```

`new_string`:
```
strip_forge_1.18plus_deps.py

# 仓库根目录散落的开发工具与测试配置（不入库）
ReobfJar.java
merge_forge1122.py
strip_forge118plus.py
update_jar_descriptions.py
update_jar_plugin.py
test-lightweight-config.yml

# Release Notes 文档（内容放在 GitHub Release body 里，不入库）
```

- [ ] **Step 3: 验证暂存区状态**

```bash
cd /workspace
git status --short
```

Expected: 7 行 `D filename`（deleted, staged）+ 1 行 `M .gitignore`（modified, unstaged）

- [ ] **Step 4: 暂存 .gitignore 并提交**

```bash
cd /workspace
git add .gitignore
git commit -m "$(cat <<'EOF'
chore: remove dev artifacts from repo

Remove 7 tracked files that violate the project rule "仓库只包含模组源码
和必要构建配置，不提交开发工具/测试脚本":

- ReobfJar.java           — ASM bytecode remapping tool (Forge 1.12.2 SRG)
- merge_forge1122.py      — Forge 1.12.2 class merger (v1.x build tool)
- strip_forge118plus.py   — Forge 1.18+ dependency stripper (similar name
                            to already-ignored strip_forge_1.18plus_deps.py)
- update_jar_descriptions.py — JAR description updater
- update_jar_plugin.py    — JAR plugin.yml updater
- test-lightweight-config.yml — lightweight test config
- release-notes-v1.2.1.md — v1.2.1 release notes (already in Release body)

.gitignore updated to ignore these 6 patterns (release-notes-*.md already
covered by existing rule) so they cannot be re-added by accident.

No version bump, no JAR rebuild — pure repo hygiene on main.
EOF
)"
```

Expected: `7 files changed, 18 deletions(+), 7 insertions(+)` 左右

- [ ] **Step 5: 验证提交成功**

```bash
cd /workspace
git log --oneline -1
git show --stat HEAD | head -15
```

Expected: commit message 显示 `chore: remove dev artifacts from repo`，stat 列出 8 个文件（7 个 deleted + 1 个 .gitignore modified）

---

### Task 2: Commit 2 — common/pom.xml 排除 StandaloneDemo

**Files:**
- Modify: `common/pom.xml:41-48`（`maven-compiler-plugin` 配置块）

- [ ] **Step 1: 修改 `common/pom.xml` 添加 excludes 配置**

使用 Edit 工具，`old_string`:
```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>${java.release}</release>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
```

`new_string`:
```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>${java.release}</release>
                    <encoding>UTF-8</encoding>
                    <!-- StandaloneDemo 是独立运行演示（main()），不应进生产 JAR -->
                    <excludes>
                        <exclude>**/StandaloneDemo.java</exclude>
                    </excludes>
                </configuration>
            </plugin>
```

- [ ] **Step 2: 验证 XML 语法正确**

```bash
cd /workspace
python3 -c "import xml.etree.ElementTree as ET; ET.parse('common/pom.xml'); print('XML OK')"
```

Expected: `XML OK`

- [ ] **Step 3: 验证 common 模块构建后不含 StandaloneDemo.class**

```bash
cd /workspace/common
mvn -q clean compile 2>&1 | tail -5
unzip -l target/HardwareBenchmark-common-*.jar 2>/dev/null | grep -i "StandaloneDemo" || echo "StandaloneDemo.class NOT in JAR (correct)"
```

Expected: `StandaloneDemo.class NOT in JAR (correct)`

如 mvn 不可用或网络受限，跳过构建验证，仅验证 XML 语法 + 视觉确认 `<excludes>` 块存在。

- [ ] **Step 4: 提交**

```bash
cd /workspace
git add common/pom.xml
git commit -m "$(cat <<'EOF'
fix: exclude StandaloneDemo from production JARs

StandaloneDemo.java (located at common/src/main/java/com/hwbench/) is a
standalone CLI demo with a main() method for testing donut rendering and
hardware detection without a running MC server. It was never intended to
ship in production JARs, but common/pom.xml did not exclude it, so
StandaloneDemo.class (3.6KB) was being shaded into bukkit-universal.jar.

Add <excludes><exclude>**/StandaloneDemo.java</exclude></excludes> to
maven-compiler-plugin in common/pom.xml. Source file is retained for
developer reference (not deleted — YAGNI does not apply to demo code
that documents API usage).

Does not affect already-published v2.0.0 JARs (no rebuild, no republish);
StandaloneDemo.class is harmless (3.6KB, only prints donut to stdout).
The fix takes effect on next build cycle.
EOF
)"
```

Expected: `1 file changed, 4 insertions(+)`

- [ ] **Step 5: 验证提交**

```bash
cd /workspace
git log --oneline -2
git show HEAD --stat
```

Expected: HEAD 显示 `fix: exclude StandaloneDemo from production JARs`，1 file changed

---

### Task 3: Commit 3 — 对齐 bug 模板、fabric.mod.json、README、TEST_REPORT

**Files:**
- Modify: `.github/ISSUE_TEMPLATE/bug_report.md:14-15`
- Modify: `fabric/src/main/resources/fabric.mod.json:24`
- Modify: `README.md:33`
- Modify: `docs/TEST_REPORT.md:99-109`

- [ ] **Step 1: 更新 `bug_report.md` 示例值为 v2.0.0**

使用 Edit 工具，`old_string`:
```
- **插件版本**：[例如 1.2.1]
- **JAR 文件名**：[例如 HardwareBenchmark-1.2.1-bukkit-java17.jar]
```

`new_string`:
```
- **插件版本**：[例如 2.0.0]
- **JAR 文件名**：[例如 HardwareBenchmark-2.0.0-bukkit-universal.jar]
```

- [ ] **Step 2: 更新 `fabric.mod.json` Java 依赖为 `>=8`**

使用 Edit 工具，`old_string`:
```
    "minecraft": ">=1.14",
    "java": ">=17"
```

`new_string`:
```
    "minecraft": ">=1.14",
    "java": ">=8"
```

- [ ] **Step 3: 更新 `README.md` Fabric "覆盖版本数" 措辞统一**

使用 Edit 工具，`old_string`:
```
| Fabric | 1.14 ~ 1.21.3（全 Fabric 支持版本） | 全部 | Java 8 / Java 17（按 MC 版本自动选择） | `HardwareBenchmark-2.0.0-fabric-universal.jar` |
```

`new_string`:
```
| Fabric | 1.14 ~ 1.21.3（全 Fabric 支持版本） | 全部版本 | Java 8 / Java 17（按 MC 版本自动选择） | `HardwareBenchmark-2.0.0-fabric-universal.jar` |
```

- [ ] **Step 4: 更新 `docs/TEST_REPORT.md` 证据索引去硬编码路径**

使用 Edit 工具，`old_string`:
```
| `/workspace/test-results/SUMMARY.log` | 16 服务器汇总表 |
| `/workspace/test-results/<server>.log` | 各服务器测试结果（含 RCON 响应、命令触发情况） |
| `/workspace/test-results/<server>.console.log` | 各服务器 stdout 原始输出（Forge HWBench 输出在此） |
| `/workspace/dist/HardwareBenchmark-2.0.0-{bukkit,fabric,forge}-universal.jar` | 3 个发布 JAR |
| `/workspace/dist/HardwareBenchmark-1.2.1-*-prev.jar` | 7 个上代 JAR（-prev 后缀，供回滚） |
| `/workspace/forge-container/` | Forge 容器 JAR 模块源码（含 `ForgeContainerBase`、`ForgeEntryClassic` 等） |
| `/workspace/build_forge_container.sh` | Forge 容器 JAR 构建脚本（Java 8 编译，手动 `javac`/`jar`） |
| `/workspace/strip_forge_1.18plus_deps.py` | Forge 1.18+ 依赖剥离脚本（移除 OSHI/JNA 避免 JPMS 分包冲突） |
| `/workspace/proc-stub/HardwareDetector.java` | /proc 硬件检测实现（Forge 1.18+ 使用，不依赖 OSHI/JNA） |
| `/workspace/test_runner.py` | 16 服务器自动化测试脚本 |
| `/workspace/deploy_universal_jars.py` | Universal JAR 部署脚本 |
```

`new_string`:
```
| `test-results/SUMMARY.log` | 16 服务器汇总表 |
| `test-results/<server>.log` | 各服务器测试结果（含 RCON 响应、命令触发情况） |
| `test-results/<server>.console.log` | 各服务器 stdout 原始输出（Forge HWBench 输出在此） |
| GitHub Release v2.0.0 资产 | 3 个发布 JAR（bukkit/fabric/forge-universal）+ 7 个 v1.2.1 `-prev` JAR |
| `forge-container/` | Forge 容器 JAR 模块源码（含 `ForgeContainerBase`、`ForgeEntryClassic` 等） |
| `build_forge_container.sh` | Forge 容器 JAR 构建脚本（Java 8 编译，手动 `javac`/`jar`） |
| `proc-stub/HardwareDetector.java` | /proc 硬件检测实现（Forge 1.18+ 使用，不依赖 OSHI/JNA） |

> 注：`test-results/` 目录在 `.gitignore` 中被忽略，证据文件仅在测试环境中生成，不入库。`test_runner.py`、`deploy_universal_jars.py`、`strip_forge_1.18plus_deps.py` 等脚本不入库（见 `.gitignore`）。
```

- [ ] **Step 5: 验证 JSON 与 Markdown 语法**

```bash
cd /workspace
python3 -c "import json; json.load(open('fabric/src/main/resources/fabric.mod.json')); print('JSON OK')"
grep -c "1.2.1" .github/ISSUE_TEMPLATE/bug_report.md || echo "0 stale 1.2.1 refs in bug_report.md (correct)"
grep -c "全部版本" README.md
grep -c "/workspace/" docs/TEST_REPORT.md
```

Expected:
- `JSON OK`
- `0 stale 1.2.1 refs in bug_report.md (correct)`（或 grep 返回非零退出码，echo 输出）
- `1`（README 中 "全部版本" 出现 1 次）
- `0`（TEST_REPORT.md 中不再有 `/workspace/` 路径）

- [ ] **Step 6: 提交**

```bash
cd /workspace
git add .github/ISSUE_TEMPLATE/bug_report.md fabric/src/main/resources/fabric.mod.json README.md docs/TEST_REPORT.md
git commit -m "$(cat <<'EOF'
docs: align bug template, README, TEST_REPORT, fabric.mod.json with v2.0.0

Four consistency fixes for content that drifted from v2.0.0 state:

1. .github/ISSUE_TEMPLATE/bug_report.md
   - 插件版本示例 1.2.1 -> 2.0.0
   - JAR 文件名示例 HardwareBenchmark-1.2.1-bukkit-java17.jar
     -> HardwareBenchmark-2.0.0-bukkit-universal.jar (Universal JAR naming)

2. fabric/src/main/resources/fabric.mod.json
   - depends.java ">=17" -> ">=8"
   - README declares "Java 8 / Java 17（按 MC 版本自动选择）" but fabric.mod.json
     required >=17, which would block Fabric 1.14-1.16.5 users on Java 8/11/16.
     Align metadata with README so Java 8+ users can load the mod.

3. README.md
   - Fabric 行 "覆盖版本数" 由 "全部" 改为 "全部版本"（与其他行格式统一）

4. docs/TEST_REPORT.md
   - 证据文件索引去除 /workspace/ 硬编码绝对路径，改用相对路径
   - 移除对不入库脚本的引用（strip_forge_1.18plus_deps.py、test_runner.py、
     deploy_universal_jars.py），补充说明这些脚本在 .gitignore 中被忽略

No version bump, no JAR rebuild — content-only fixes on main.
EOF
)"
```

Expected: `4 files changed, X insertions(+), Y deletions(-)`

- [ ] **Step 7: 验证提交**

```bash
cd /workspace
git log --oneline -3
git show HEAD --stat
```

Expected: HEAD 显示 `docs: align bug template...`，4 files changed

---

### Task 4: Commit 4 — build_forge_container.sh 可移植化 + .gitignore 清理

**Files:**
- Modify: `build_forge_container.sh`（全文件重写路径部分）
- Modify: `.gitignore:38-40`（移除死规则）

- [ ] **Step 1: 重写 `build_forge_container.sh` 去硬编码路径**

使用 Write 工具，将整个文件替换为以下内容（保留原有逻辑，仅替换路径与版本号读取方式）：

```bash
#!/bin/bash
# Build the Forge universal container JAR (forge-container).
# Compiles with Java 8 against stub annotation types, then stages and packages.
set -e

# Resolve script directory (portable: works from any cwd)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/forge-container"

# Locate Java 8 toolchain: prefer $JAVA8_HOME, fall back to PATH lookup
if [ -n "$JAVA8_HOME" ]; then
    JAVA8="$JAVA8_HOME/bin/java"
    JAVAC8="$JAVA8_HOME/bin/javac"
    JAR8="$JAVA8_HOME/bin/jar"
else
    JAVA8="${JAVA8:-java}"
    JAVAC8="${JAVAC8:-javac}"
    JAR8="${JAR8:-jar}"
fi

# Read version from ForgeContainerBase.java (single source of truth)
VERSION=$(grep -oE 'VERSION = "[^"]+"' src/main/java/com/hwbench/forge/container/ForgeContainerBase.java \
    | head -1 | cut -d'"' -f2)
if [ -z "$VERSION" ]; then
    echo "ERROR: could not read VERSION from ForgeContainerBase.java" >&2
    exit 1
fi
echo "Building Forge container JAR v$VERSION"

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
DIST="$SCRIPT_DIR/dist"
mkdir -p "$DIST"
OUT_JAR="$DIST/HardwareBenchmark-${VERSION}-forge-universal.jar"
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
```

- [ ] **Step 2: 验证脚本语法**

```bash
cd /workspace
bash -n build_forge_container.sh && echo "Bash syntax OK"
grep -c "/workspace/\|/root/.local" build_forge_container.sh
```

Expected:
- `Bash syntax OK`
- `0`（脚本中不再含 `/workspace/` 或 `/root/.local`）

- [ ] **Step 3: 验证脚本能正确读取版本号**

```bash
cd /workspace/forge-container
VERSION=$(grep -oE 'VERSION = "[^"]+"' src/main/java/com/hwbench/forge/container/ForgeContainerBase.java | head -1 | cut -d'"' -f2)
echo "Detected VERSION: $VERSION"
test "$VERSION" = "2.0.0" && echo "VERSION matches 2.0.0 (correct)" || echo "VERSION mismatch (expected 2.0.0)"
```

Expected: `Detected VERSION: 2.0.0` + `VERSION matches 2.0.0 (correct)`

- [ ] **Step 4: 清理 `.gitignore` 死规则**

使用 Edit 工具，`old_string`:
```
# 服务器 JAR 和库文件（不需要版本控制）
*.jar
!dist/*.jar

# Maven 设置（含凭据）
```

`new_string`:
```
# 服务器 JAR 和库文件（不需要版本控制；dist/ 单独忽略见下）
*.jar

# Maven 设置（含凭据）
```

- [ ] **Step 5: 验证 .gitignore 规则无矛盾**

```bash
cd /workspace
grep -nE '^\*\.jar$|^\!dist/\*\.jar$|^dist/$' .gitignore
```

Expected: 仅出现 `*.jar`（约第 39 行）和 `dist/`（约第 62 行），**不再出现** `!dist/*.jar`（已删除）。

- [ ] **Step 6: 提交**

```bash
cd /workspace
git add build_forge_container.sh .gitignore
git commit -m "$(cat <<'EOF'
build: make forge-container script portable + clean .gitignore

build_forge_container.sh:
- Replace hardcoded /workspace/ paths with $SCRIPT_DIR (resolved from
  script location via dirname), so the script works from any checkout
- Replace hardcoded /root/.local/share/mise/installs/java/temurin-8.0.482+8
  toolchain path with $JAVA8_HOME env var, falling back to PATH lookup
  (java/javac/jar) when unset
- Read VERSION dynamically from ForgeContainerBase.java VERSION field
  instead of hardcoding "2.0.0" in OUT_JAR filename — single source of
  truth, aligns with project rule "版本号需同步全量更新"

.gitignore:
- Remove dead rule pair "*.jar" + "!dist/*.jar" — the "!dist/*.jar"
  negation contradicts the "dist/" rule below it and was never effective
  (dist/ is ignored wholesale). Keep "*.jar" for stray JARs, keep
  "dist/" for the dist directory.

No version bump, no JAR rebuild — build tooling portability fix only.
EOF
)"
```

Expected: `2 files changed, X insertions(+), Y deletions(-)`

- [ ] **Step 7: 验证提交**

```bash
cd /workspace
git log --oneline -4
git show HEAD --stat
```

Expected: HEAD 显示 `build: make forge-container script portable + clean .gitignore`，2 files changed

---

### Task 5: 推送 4 个 commit 到 origin/main

**Files:** 无文件操作，仅 git push

- [ ] **Step 1: 推送前最终验证本地状态**

```bash
cd /workspace
echo "=== Last 5 commits ==="
git log --oneline -5
echo ""
echo "=== Working tree clean? ==="
git status --short
echo ""
echo "=== 4 new commits ahead of origin/main ==="
git log --oneline origin/main..HEAD
```

Expected:
- 4 个新 commit 按时间倒序排列（build → docs → fix → chore）
- 工作树干净（无 untracked / modified）
- `git log origin/main..HEAD` 列出 4 个 commit

- [ ] **Step 2: 推送到 origin/main**

```bash
cd /workspace
git push origin main 2>&1 | tail -10
```

Expected: `To github.com:waiyi233ye/HardwareBenchmark.git` + `6e57dce..<new_sha>  main -> main`

- [ ] **Step 3: 验证远程 main 已更新**

通过 GitHub MCP 工具验证（不依赖本地 git）：

调用 `list_commits` MCP 工具，参数 `{"owner": "waiyi233ye", "repo": "HardwareBenchmark", "perPage": 6}`，预期返回的最近 5 个 commit message 为：
1. `build: make forge-container script portable + clean .gitignore`
2. `docs: align bug template, README, TEST_REPORT, fabric.mod.json with v2.0.0`
3. `fix: exclude StandaloneDemo from production JARs`
4. `chore: remove dev artifacts from repo`
5. `release: v2.0.0 — Universal JAR for 1.7.10-1.21.3 (3 JAR replaces 7)`

- [ ] **Step 4: 通过 GitHub MCP 验证 dev artifacts 已删除**

依次调用 `get_file_contents` MCP 工具（参数 `{"owner": "waiyi233ye", "repo": "HardwareBenchmark", "path": "ReobfJar.java"}`），预期返回 404 "Not Found"。

重复检查以下 7 个路径均返回 404：
- `ReobfJar.java`
- `merge_forge1122.py`
- `strip_forge118plus.py`
- `update_jar_descriptions.py`
- `update_jar_plugin.py`
- `test-lightweight-config.yml`
- `release-notes-v1.2.1.md`

- [ ] **Step 5: 通过 GitHub MCP 验证 Release v2.0.0 资产未受影响**

调用 `get_release_by_tag` MCP 工具（参数 `{"owner": "waiyi233ye", "repo": "HardwareBenchmark", "tag": "v2.0.0"}`），预期：
- `tag_name`: `v2.0.0`
- `assets.length`: `10`（3 新 + 7 prev，未变化）
- 第一个资产名: `HardwareBenchmark-2.0.0-bukkit-universal.jar`

- [ ] **Step 6: 验证 v1.2.1 Release 仍保留**

调用 `get_release_by_tag` MCP 工具（参数 `{"owner": "waiyi233ye", "repo": "HardwareBenchmark", "tag": "v1.2.1"}`），预期：
- `tag_name`: `v1.2.1`
- `assets.length`: `14`（原样保留，未受清理影响）

---

### Task 6: 更新 spec tasks.md/checklist.md 收尾

**Files:**
- Modify: `.trae/specs/universal-multi-version-jar/tasks.md`（追加 Phase 8）
- Modify: `.trae/specs/universal-multi-version-jar/checklist.md`（追加 Phase 8）

- [ ] **Step 1: 在 `tasks.md` 末尾追加 Phase 8**

在 `# Task Dependencies` 行之前插入：

```markdown
## Phase 8: 仓库内容优化（v2.0.0 后清理）

- [x] Task 15: 清理 main 分支残留 dev artifacts 并对齐文档
  - [x] SubTask 15.1: 删除 7 个 dev artifacts（ReobfJar.java、4 个 .py、test-lightweight-config.yml、release-notes-v1.2.1.md）+ .gitignore 补规则
  - [x] SubTask 15.2: common/pom.xml 排除 StandaloneDemo，防止演示类被打包进 bukkit-universal.jar
  - [x] SubTask 15.3: 对齐 bug_report.md（v1.2.1 → v2.0.0）、fabric.mod.json（java>=17 → java>=8）、README（Fabric "全部" → "全部版本"）、TEST_REPORT（去 /workspace/ 硬编码路径）
  - [x] SubTask 15.4: build_forge_container.sh 可移植化（$SCRIPT_DIR + $JAVA8_HOME + 动态 VERSION 读取）+ .gitignore 移除死规则
  - [x] SubTask 15.5: 4 个 commit 推送到 origin/main，GitHub MCP 验证通过（dev artifacts 404、Release 资产未变）
```

- [ ] **Step 2: 在 `checklist.md` 末尾追加 Phase 8**

在文件末尾追加：

```markdown
## Phase 8: 仓库内容优化（v2.0.0 后清理）

- [x] 仓库不含 7 个 dev artifacts（ReobfJar.java、merge_forge1122.py、strip_forge118plus.py、update_jar_descriptions.py、update_jar_plugin.py、test-lightweight-config.yml、release-notes-v1.2.1.md）
- [x] `.gitignore` 含上述 6 个文件模式的忽略规则（release-notes-*.md 已有覆盖）
- [x] `common/pom.xml` 的 `maven-compiler-plugin` 含 `<excludes><exclude>**/StandaloneDemo.java</exclude></excludes>`
- [x] `.github/ISSUE_TEMPLATE/bug_report.md` 示例值为 `2.0.0` 和 `HardwareBenchmark-2.0.0-bukkit-universal.jar`
- [x] `fabric/src/main/resources/fabric.mod.json` 的 `depends.java` 为 `">=8"`（与 README 一致）
- [x] `README.md` Fabric 行 "覆盖版本数" 为 "全部版本"（与其他行格式统一）
- [x] `docs/TEST_REPORT.md` 证据索引无 `/workspace/` 硬编码路径，不引用不入库脚本
- [x] `build_forge_container.sh` 不含 `/workspace/` 或 `/root/.local/` 硬编码路径
- [x] `build_forge_container.sh` 的 OUT_JAR 版本号从 `ForgeContainerBase.java` 动态读取
- [x] `.gitignore` 不含 `!dist/*.jar` 死规则（与 `dist/` 矛盾）
- [x] 4 个 commit 已推送到 `origin/main`（chore/fix/docs/build）
- [x] GitHub MCP 验证：7 个 dev artifacts 路径返回 404
- [x] GitHub MCP 验证：Release v2.0.0 资产数仍为 10（未受影响）
- [x] GitHub MCP 验证：Release v1.2.1 资产数仍为 14（未受影响）
```

- [ ] **Step 3: 提交 spec 文档更新**

```bash
cd /workspace
git add .trae/specs/universal-multi-version-jar/tasks.md .trae/specs/universal-multi-version-jar/checklist.md
git commit -m "$(cat <<'EOF'
docs(spec): mark Phase 8 (repo content optimization) complete

Append Phase 8 to tasks.md and checklist.md documenting the 4-commit
cleanup (chore/fix/docs/build) that removed dev artifacts, excluded
StandaloneDemo from production JARs, aligned docs with v2.0.0, and
made build_forge_container.sh portable.

All 14 Phase 8 checkpoints verified via GitHub MCP (dev artifacts 404,
Release v2.0.0 assets=10, Release v1.2.1 assets=14).
EOF
)"
git push origin main 2>&1 | tail -5
```

Expected: `2 files changed` + push success

---

## Verification Summary

执行完所有 Task 后，最终验证清单：

1. **本地 git log** 显示 5 个新 commit（chore/fix/docs/build/docs(spec)）
2. **GitHub MCP `list_commits`** 返回相同的 5 个 commit（顺序一致）
3. **GitHub MCP `get_file_contents`** 对 7 个 dev artifacts 路径均返回 404
4. **GitHub MCP `get_release_by_tag` v2.0.0** 返回 assets.length=10
5. **GitHub MCP `get_release_by_tag` v1.2.1** 返回 assets.length=14
6. **本地 `bash -n build_forge_container.sh`** 通过语法检查
7. **本地 `grep '/workspace/\|/root/.local' build_forge_container.sh`** 返回 0 行
8. **本地 `python3 -c "import json; json.load(open('fabric/src/main/resources/fabric.mod.json'))"`** 输出 `JSON OK` 且 `java` 字段为 `>=8`
9. **本地 `grep '1.2.1' .github/ISSUE_TEMPLATE/bug_report.md`** 返回 0 行
10. **本地 `grep '/workspace/' docs/TEST_REPORT.md`** 返回 0 行

---

## Notes

- **不升版本号**：v2.0.0 已发布且 100% 测试通过，本次清理不构成功能/Bug 修复。`StandaloneDemo.class` 是无害演示代码（3.6KB，仅含 `main()` 打印甜甜圈），不影响已发布 JAR 的功能。
- **不重打包 JAR**：3 个 v2.0.0 Universal JAR 保持现状，不重新构建上传。`common/pom.xml` 的 excludes 修复在下次构建周期生效。
- **不发新 Release**：v2.0.0 Release 的 10 个资产不变。
- **不开 PR**：个人维护仓库，4 个分类 commit 直推 main 最实用。
