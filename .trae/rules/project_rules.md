# HardwareBenchmark 项目规则（每次对话必读）

## 版本号更新规则

当对本项目进行任何修改并需要重新发布时，必须遵循以下版本号更新规则：

### 版本号格式
`MAJOR.MINOR.PATCH`（语义化版本 SemVer）

### 版本号变更条件
- **PATCH（补丁版，如 1.2.0 → 1.2.1）**：Bug 修复、测试通过率提升、描述优化、不改变功能的内部重构
- **MINOR（次版，如 1.2.1 → 1.3.0）**：新增功能、新增支持的 MC 版本、兼容性扩展
- **MAJOR（主版，如 1.x → 2.0.0）**：破坏性变更、API 不兼容、大规模重写

### 版本号需同步更新的位置（全量更新，不可遗漏）
更新版本号时，必须同时更新以下所有文件中的版本字符串：

1. **Maven POM**：`pom.xml`、`common/pom.xml`、`bukkit/pom.xml`（`<version>` 标签）
2. **Gradle（子 JAR 源项目）**：`fabric/build.gradle`、`forge/build.gradle`、`forge-1.7.10/build.gradle`、`forge-1.12.2/build.gradle`、`forge-1.16.5/build.gradle`（`version` 属性）
3. **forge-container 模块（无 `build.gradle`，通过脚本手动调用 `javac`/`jar` 构建，版本字符串硬编码在以下文件中）**：
   - `forge-container/src/main/java/com/hwbench/forge/container/ForgeContainerBase.java`（`VERSION` 字段）
   - `forge-container/src/main/resources/META-INF/MANIFEST.MF`（`Implementation-Version`）
   - `forge-container/src/main/resources/META-INF/mods.toml`（`version` 字段，Forge 1.14–1.20.1）
   - `forge-container/src/main/resources/META-INF/neoforge.mods.toml`（`version` 字段，NeoForge 1.20.2+）
   - `forge-container/src/main/resources/mcmod.info`（`version` 字段，Forge 1.7.10 / 1.12.2）
4. **Bukkit 元数据**：`bukkit/src/main/resources/plugin.yml`（使用 `${project.version}` 占位符，Maven 自动替换，无需手动改）
5. **Fabric 元数据**：`fabric/src/main/resources/fabric.mod.json`（`version` 字段使用 `${version}` 占位符，Gradle 自动替换，无需手动改）
6. **Forge 子项目元数据**（这些子项目产出内嵌进 `forge-container` 的 sub-JAR）：
   - `forge/src/main/resources/META-INF/mods.toml`（`version` 字段，Forge 1.18+）
   - `forge-1.16.5/src/main/resources/META-INF/mods.toml`
   - `forge-1.12.2/src/main/resources/mcmod.info`
   - `forge-1.7.10/src/main/resources/mcmod.info`
7. **Forge 子项目 Java 源码（硬编码版本号）**：
   - `forge-1.7.10/src/main/java/com/hwbench/forge/HWBenchForge1710.java`（`@Mod(... version = "...")`）
   - `forge-1.12.2/src/main/java/com/hwbench/forge/HWBenchForge1122.java`（`@Mod(... version = "...")`）
8. **README.md**：兼容版本矩阵中的 JAR 文件名、Release 下载链接
9. **GitHub Release**：JAR 文件名（`HardwareBenchmark-{版本}-{平台}.jar`）、Release 标签（`v{版本}`）、Release 描述

> 注：v2.0.0 起，`forge-1.7.10/`、`forge-1.12.2/`、`forge-1.16.5/`、`forge/`（1.18+）四个子项目仍保留源码与各自的 `build.gradle`，但它们产出的 JAR 不再作为独立发布物，而是作为 sub-JAR 内嵌进 `forge-container/` 通用 Forge JAR 的 `META-INF/jars/` 目录。因此这些子项目内的版本字符串仍需同步更新，以保证内嵌 sub-JAR 的元数据与外层容器版本一致。

### 发布 JAR 命名规则
`HardwareBenchmark-{版本}-{平台}.jar`

> 自 v2.0.0 起，发布物由 7 个 JAR 精简为 3 个 Universal JAR，平台标识 ∈ {`bukkit-universal`, `fabric-universal`, `forge-universal`}。

平台标识：
- `bukkit-universal`：Bukkit/Spigot/Paper，覆盖 MC 1.7.10–1.21.3
- `fabric-universal`：Fabric，覆盖 MC 1.14–1.21.3
- `forge-universal`：Forge，覆盖 MC 1.7.10–1.21.3（含 NeoForge 1.20.2+）

v2.0.0 发布物示例：
1. `HardwareBenchmark-2.0.0-bukkit-universal.jar`
2. `HardwareBenchmark-2.0.0-fabric-universal.jar`
3. `HardwareBenchmark-2.0.0-forge-universal.jar`

> 已废弃的旧平台标识（v1.x，v2.0.0 起不再使用）：`bukkit-java8`、`bukkit-java17`、`forge-1.7.10`、`forge-1.12.2`、`forge-1.16.5`、`forge-1.18plus`。注：`fabric-universal` 标识在 v1.x 与 v2.0.0 间沿用同名，仅 MC 覆盖范围扩展，不属于废弃标识。

### Release 资产保留规则
发布新版本时，上一代 JAR 文件需保留在 Release 资产中，文件名添加 `-prev` 后缀供用户回滚对比。

- 保留数量 = 上一代发布物的 JAR 数量（即上一版 Release 中上传的所有 JAR）
- v2.0.0 发布时：上一代为 v1.2.1（7 JAR），需保留 7 个 `-prev` JAR：
  - `HardwareBenchmark-1.2.1-bukkit-java8-prev.jar`
  - `HardwareBenchmark-1.2.1-bukkit-java17-prev.jar`
  - `HardwareBenchmark-1.2.1-fabric-universal-prev.jar`
  - `HardwareBenchmark-1.2.1-forge-1.7.10-prev.jar`
  - `HardwareBenchmark-1.2.1-forge-1.12.2-prev.jar`
  - `HardwareBenchmark-1.2.1-forge-1.16.5-prev.jar`
  - `HardwareBenchmark-1.2.1-forge-1.18plus-prev.jar`
- v2.0.0 之后的下一个版本发布时：上一代为 v2.0.0（3 JAR），需保留 3 个 `-prev` JAR（即 3 个 Universal JAR 的 `-prev` 副本）。

### 测试验证规则
版本更新后必须通过实机测试验证：
- 16 个 MC 服务器（Bukkit 6 + Fabric 4 + Forge 6）全部启动成功
- 9 个 `/hwbench` 子命令在所有服务器上触发率 100%
- 测试证据归档到 `test-results/` 目录并同步到 `docs/TEST_REPORT.md`

## 项目结构规则
- 仓库只包含模组源码和必要构建配置，不提交构建产物（`.gradle/`、`build/`、`target/`、`*.class`、`dist/`）
- JAR 文件通过 GitHub Release Assets 分发，不提交到仓库
- 测试脚本（`test_*.py`）和开发工具不提交到仓库

### 模块组成（v2.0.0 通用 JAR 架构）
- **`common/`**：跨平台核心基准逻辑（Maven 构建，Java 8 字节码），被 bukkit 直接打包、被 fabric/forge 子项目依赖。
- **`bukkit/`**：Bukkit/Spigot/Paper 通用 JAR 模块，使用 **MRJAR（Multi-Release JAR）**，含版本特定源码目录 `src/main/java`（Java 8 基线）、`src/main/java17`、`src/main/java21`，由 Maven 自动合并为单 JAR，覆盖 MC 1.7.10–1.21.3 全部 Java 版本。
- **`fabric/`**：Fabric 通用 JAR 模块，使用 **反射式跨版本兼容**（单一代码入口 `HWBenchFabric.java`），通过运行时反射调用不同 MC 版本的 API，产出 `fabric-universal` JAR。
- **`forge-container/`**：**v2.0.0 新增模块**，产出 `forge-universal` 通用 Forge JAR。无 `build.gradle`，通过脚本（`build_forge_container.sh`）手动调用 `javac`/`jar` 构建，外层容器 JAR 的 `META-INF/jars/` 目录内嵌 4 个 sub-JAR。
- **`forge/`、`forge-1.7.10/`、`forge-1.12.2/`、`forge-1.16.5/`**：4 个 Forge 子项目（各有独立 `build.gradle`），分别产出对应 MC 时代的 sub-JAR，构建后被嵌入 `forge-container` JAR 的 `META-INF/jars/`，**不再作为独立发布物**。
- **`proc-stub/`、`stubs/`**：编译期注解处理与 API 桩，用于在不依赖完整 MC 服务端依赖的前提下编译容器与子项目。
