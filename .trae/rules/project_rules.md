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
2. **Gradle**：`fabric/build.gradle`、`forge/build.gradle`、`forge-1.7.10/build.gradle`、`forge-1.12.2/build.gradle`、`forge-1.16.5/build.gradle`（`version` 属性）
3. **Bukkit 元数据**：`bukkit/src/main/resources/plugin.yml`（使用 `${project.version}` 占位符，Maven 自动替换，无需手动改）
4. **Fabric 元数据**：`fabric/src/main/resources/fabric.mod.json`（`version` 字段）
5. **Forge 元数据**：
   - `forge/src/main/resources/META-INF/mods.toml`（`version` 字段）
   - `forge-1.16.5/src/main/resources/META-INF/mods.toml`
   - `forge-1.7.10/src/main/resources/mcmod.info`（`version` 字段）
   - `forge-1.12.2/src/main/resources/mcmod.info`
6. **Forge Java 源码**（硬编码版本号）：
   - `forge-1.7.10/src/main/java/com/hwbench/forge/HWBenchForge1710.java`
   - `forge-1.12.2/src/main/java/com/hwbench/forge/HWBenchForge1122.java`
7. **README.md**：兼容版本矩阵中的 JAR 文件名、Release 下载链接
8. **GitHub Release**：JAR 文件名（`HardwareBenchmark-{版本}-{平台}.jar`）、Release 标签（`v{版本}`）、Release 描述

### 发布 JAR 命名规则
`HardwareBenchmark-{版本}-{平台}.jar`

平台标识：
- `bukkit-java8`：Bukkit/Spigot/Paper，Java 8（MC 1.7.10, 1.12.2）
- `bukkit-java17`：Bukkit/Spigot/Paper，Java 17（MC 1.16.5, 1.18.2, 1.19.2, 1.20.1）
- `fabric-universal`：Fabric，Java 17（MC 1.16.5–1.20.1）
- `forge-1.7.10`：Forge 1.7.10，Java 8
- `forge-1.12.2`：Forge 1.12.2，Java 8
- `forge-1.16.5`：Forge 1.16.5，Java 8
- `forge-1.18plus`：Forge 1.18.2/1.19.2/1.20.1，Java 17

### Release 资产保留规则
发布新版本时，上一代 JAR 文件需保留在 Release 资产中，文件名添加 `-prev` 后缀（如 `HardwareBenchmark-1.2.1-bukkit-java8-prev.jar`），供用户回滚对比。

### 测试验证规则
版本更新后必须通过实机测试验证：
- 16 个 MC 服务器（Bukkit 6 + Fabric 4 + Forge 6）全部启动成功
- 9 个 `/hwbench` 子命令在所有服务器上触发率 100%
- 测试证据归档到 `test-results/` 目录并同步到 `docs/TEST_REPORT.md`

## 项目结构规则
- 仓库只包含模组源码和必要构建配置，不提交构建产物（`.gradle/`、`build/`、`target/`、`*.class`、`dist/`）
- JAR 文件通过 GitHub Release Assets 分发，不提交到仓库
- 测试脚本（`test_*.py`）和开发工具不提交到仓库
