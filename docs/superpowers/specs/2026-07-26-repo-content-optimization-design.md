# HardwareBenchmark 仓库内容优化 Design

> **Date**: 2026-07-26
> **Topic**: repo-content-optimization
> **Status**: Draft (awaiting user approval)
> **Predecessor**: v2.0.0 已发布于 GitHub（commit `6e57dce`，Release v2.0.0 with 10 assets）

## Why

v2.0.0 已发布且 16 服务器 144/144 命令全部通过，但仓库 `main` 分支仍残留 7 个开发产物被追踪、`StandaloneDemo.class` 泄漏进生产 JAR、Issue 模板示例值停留在 v1.2.1、Fabric 元数据 `java>=17` 与 README 声明矛盾、构建脚本硬编码本地路径。这些问题违反 `.trae/rules/project_rules.md` 的"仓库只包含模组源码和必要构建配置"原则，需在 main 分支上做一次内容清理（不升版本号、不重打包、不发新 Release）。

## What Changes

四个分类 commit 直推 `main` 分支：

### Commit 1: `chore: remove dev artifacts from repo`
**删除 7 个文件**（违反"不提交开发工具/测试脚本"规则）：
- `ReobfJar.java` — ASM 字节码重映射工具类（根目录散落文件）
- `merge_forge1122.py` — Forge 1.12.2 类合并脚本
- `strip_forge118plus.py` — Forge 1.18+ 依赖剥离脚本（与已忽略的 `strip_forge_1.18plus_deps.py` 文件名相似易混淆）
- `update_jar_descriptions.py` — JAR 描述更新脚本
- `update_jar_plugin.py` — JAR plugin.yml 更新脚本
- `test-lightweight-config.yml` — 测试用轻量配置
- `release-notes-v1.2.1.md` — v1.2.1 发行说明（内容已在 v1.2.1 Release body 里）

**更新 `.gitignore`**：新增 4 个 Python 脚本与 `ReobfJar.java` 的忽略规则（`release-notes-*.md` 已在 v2.0.0 commit 中添加，`test-lightweight-config.yml` 归入测试配置忽略）。

### Commit 2: `fix: exclude StandaloneDemo from production JARs`
**修改 `common/pom.xml`**：在 `maven-compiler-plugin` 配置中添加 `<excludes><exclude>**/StandaloneDemo.java</exclude></excludes>`，确保 `StandaloneDemo.class` 不被打包进 common JAR，进而不会通过 shade 进入 `bukkit-universal.jar`。

源文件 `common/src/main/java/com/hwbench/StandaloneDemo.java` 保留（开发参考用途，选项 a）。

### Commit 3: `docs: align bug template, README, TEST_REPORT, fabric.mod.json with v2.0.0`
**修改 4 个文件**：
1. `.github/ISSUE_TEMPLATE/bug_report.md` — 第 14 行 `1.2.1` → `2.0.0`；第 15 行 `HardwareBenchmark-1.2.1-bukkit-java17.jar` → `HardwareBenchmark-2.0.0-bukkit-universal.jar`
2. `fabric/src/main/resources/fabric.mod.json` — 第 24 行 `"java": ">=17"` → `"java": ">=8"`（与 README "Java 8/17 自动选择" 一致，让 Fabric 1.14-1.16.5 的 Java 8/11/16 用户可加载）
3. `README.md` — 第 33 行 Fabric "覆盖版本数" `全部` → `全部版本`（与其他行格式统一，保留语义准确）；第 34 行 Forge "1.7.10 ~ 1.21.3（含 NeoForge 1.20.2+）" 保留（已准确）
4. `docs/TEST_REPORT.md` — 第 95-109 行证据索引：将所有 `/workspace/...` 绝对路径替换为相对路径（`test-results/SUMMARY.log`、`dist/HardwareBenchmark-2.0.0-*.jar` 等），并移除对 `strip_forge_1.18plus_deps.py`、`test_runner.py`、`deploy_universal_jars.py` 等不入库脚本的引用（仅保留仓库内文件的引用）

### Commit 4: `build: make forge-container script portable + clean .gitignore`
**修改 2 个文件**：
1. `build_forge_container.sh` — 移除硬编码路径：
   - 第 6 行 `cd /workspace/forge-container` → `cd "$(dirname "$0")/forge-container"`（脚本所在目录的子目录）
   - 第 8-10 行 `/root/.local/share/mise/installs/java/temurin-8.0.482+8/bin/{java,javac,jar}` → 通过 `JAVA8_HOME` 环境变量检测，回退到 `java`/`javac`/`jar`（PATH 查找）
   - 第 48 行 `DIST=/workspace/dist` → `DIST="$(dirname "$0")/dist"`
   - 第 50 行 `OUT_JAR="$DIST/HardwareBenchmark-2.0.0-forge-universal.jar"` → 版本号从 `ForgeContainerBase.java` 的 `VERSION` 字段读取（`VERSION=$(grep -oE 'VERSION = "[^"]+"' src/main/java/com/hwbench/forge/container/ForgeContainerBase.java | cut -d'"' -f2)`）
2. `.gitignore` — 移除第 39-40 行的 `*.jar` + `!dist/*.jar` 死规则（第 62 行 `dist/` 已覆盖，且 `!dist/*.jar` 与 `dist/` 矛盾）

**BREAKING**：无（仅清理 main 分支内容，不改变已发布 JAR 的功能；v2.0.0 Release 资产不变）

## Impact

- **Affected specs**: `universal-multi-version-jar`（已完成的 v2.0.0 发布，本次为后续清理）
- **Affected code**:
  - 删除：7 个根目录 dev artifacts
  - 修改：`common/pom.xml`、`.github/ISSUE_TEMPLATE/bug_report.md`、`fabric/src/main/resources/fabric.mod.json`、`README.md`、`docs/TEST_REPORT.md`、`build_forge_container.sh`、`.gitignore`
- **不影响**：已发布的 3 个 v2.0.0 Universal JAR（不重打包）、GitHub Release v2.0.0 的 10 个资产、v1.2.1/v1.2.0 Release

## ADDED Requirements

### Requirement: 仓库不含开发产物
系统 SHALL 确保仓库 `main` 分支不含以下开发产物：`ReobfJar.java`、`merge_forge1122.py`、`strip_forge118plus.py`、`update_jar_descriptions.py`、`update_jar_plugin.py`、`test-lightweight-config.yml`、`release-notes-v1.2.1.md`。

#### Scenario: 仓库无开发工具
- **WHEN** 检查仓库根目录文件列表
- **THEN** 不存在上述 7 个文件
- **AND** `.gitignore` 包含 `ReobfJar.java`、`merge_forge1122.py`、`strip_forge118plus.py`、`update_jar_descriptions.py`、`update_jar_plugin.py`、`test-lightweight-config.yml` 的忽略规则

### Requirement: 生产 JAR 不含演示代码
系统 SHALL 确保 `StandaloneDemo.class` 不出现在任何发布 JAR 中。

#### Scenario: common 模块排除 StandaloneDemo
- **WHEN** 读取 `common/pom.xml` 的 `maven-compiler-plugin` 配置
- **THEN** 包含 `<excludes><exclude>**/StandaloneDemo.java</exclude></excludes>`
- **AND** 重新构建 `bukkit-universal.jar` 后，`unzip -l` 输出不含 `com/hwbench/StandaloneDemo.class`

### Requirement: Issue 模板示例与当前版本一致
系统 SHALL 确保 `.github/ISSUE_TEMPLATE/bug_report.md` 的示例值反映当前发布版本。

#### Scenario: 模板示例为 v2.0.0
- **WHEN** 读取 `bug_report.md`
- **THEN** 插件版本示例为 `2.0.0`
- **AND** JAR 文件名示例为 `HardwareBenchmark-2.0.0-bukkit-universal.jar`（Universal JAR 命名）

### Requirement: Fabric 元数据 Java 版本与 README 一致
系统 SHALL 确保 `fabric.mod.json` 的 `java` 依赖范围与 README 声明一致。

#### Scenario: Fabric 支持 Java 8+
- **WHEN** 读取 `fabric/src/main/resources/fabric.mod.json`
- **THEN** `depends.java` 为 `">=8"`
- **AND** README 中 Fabric 行的 Java 版本描述为 "Java 8 / Java 17（按 MC 版本自动选择）"

### Requirement: 构建脚本可移植
系统 SHALL 确保 `build_forge_container.sh` 不含硬编码的本地绝对路径。

#### Scenario: 脚本使用相对路径与环境变量
- **WHEN** 读取 `build_forge_container.sh`
- **THEN** 不含 `/workspace/` 或 `/root/.local/share/mise/` 字符串
- **AND** Java 8 工具链通过 `JAVA8_HOME` 环境变量或 PATH 查找定位
- **AND** 输出 JAR 文件名的版本号从 `ForgeContainerBase.java` 的 `VERSION` 字段读取（不硬编码）

### Requirement: .gitignore 规则无矛盾
系统 SHALL 确保 `.gitignore` 不含相互矛盾的规则。

#### Scenario: dist/ 规则无冲突
- **WHEN** 读取 `.gitignore`
- **THEN** 不存在 `*.jar` 后紧跟 `!dist/*.jar` 的死规则对
- **AND** `dist/` 规则单独存在且生效

## MODIFIED Requirements

### Requirement: TEST_REPORT 证据索引可移植
`docs/TEST_REPORT.md` 的证据文件索引 SHALL 使用相对于仓库根目录的路径，不使用 `/workspace/` 绝对路径；且仅引用仓库内存在的文件，不引用不入库的开发脚本。

#### Scenario: 证据索引使用相对路径
- **WHEN** 读取 `docs/TEST_REPORT.md` 第 5 节"证据文件索引"
- **THEN** 所有路径以 `test-results/`、`dist/`、`forge-container/`、`proc-stub/` 等相对路径开头
- **AND** 不引用 `strip_forge_1.18plus_deps.py`、`test_runner.py`、`deploy_universal_jars.py` 等不入库脚本

## REMOVED Requirements

无（本次为清理与一致性修复，不移除任何功能）

## 实施约束

- **不升版本号**：v2.0.0 已发布且 100% 测试通过，本次清理不构成功能/Bug 修复，无需发布 v2.0.1。`StandaloneDemo.class` 是无害演示代码（3.6KB，仅含 `main()` 打印甜甜圈），不影响已发布 JAR 的功能。
- **不重打包 JAR**：3 个 v2.0.0 Universal JAR 保持现状，不重新构建上传。
- **不发新 Release**：v2.0.0 Release 的 10 个资产不变。
- **直推 main**：4 个分类 commit 直接 push 到 `origin/main`，不开 PR（个人维护仓库，PR 过度工程）。
- **使用 GitHub 插件**：本地 git 完成多文件分类 commit（`push_files` MCP 工具无法删除文件且每次只能一个 commit，不适合本场景），推送后通过 `trae-remote-official:github` 的 MCP 工具（`list_commits`、`get_file_contents`）验证仓库状态。
