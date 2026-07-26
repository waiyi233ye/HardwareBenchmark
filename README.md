# HardwareBenchmark

> MC Java 版服务端硬件检测与跑分插件（Bukkit / Fabric / Forge，兼容 1.7.10 ~ 1.21.3）

## 简介

HardwareBenchmark 是一款 Minecraft Java 版服务端插件，用于检测服务器硬件信息并执行 CPU/内存/磁盘跑分。支持三大主流模组加载器（Bukkit/Spigot/Paper、Fabric、Forge），覆盖从 1.7.10 到 1.21.3 的全部主流版本（共 59 个 MC 版本）。v2.0.0 起采用通用 JAR 架构，三大平台各仅发布 1 个 JAR，大幅简化下载选择。

### 创作背景

因租用的面板服一直卡顿、被卡得不耐烦，我用 AI 编写了这款硬件检测与跑分插件。

### AI 编写声明

本项目的代码、文档与构建脚本均由 AI 辅助生成（我负责需求设计、测试验证与发布维护）。使用前请自行审查代码、确认其行为符合预期；作者不对插件在特定环境下的运行结果作任何担保。

## 功能

- 🔍 **硬件检测**：检测 CPU 型号、内存容量、磁盘信息、操作系统（Bukkit/Fabric 基于 OSHI，Forge 1.18+ 基于 /proc 文件系统）
- ⚡ **CPU 跑分**：素数计算 + 甜甜圈渲染，多线程测试
- 💾 **内存跑分**：大数组读写 + 内存拷贝带宽测试
- 💿 **磁盘跑分**：顺序/随机读写 IO 测试
- 🔒 **服务器锁定**：跑分期间可锁定服务器，踢出在线玩家并阻止新玩家加入
- 📦 **库检查**：自动检测并补全 Linux 运行所需系统库（lshw, lm-sensors, pciutils, smartmontools）

## 兼容版本矩阵

v2.0.0 起采用**通用 JAR（Universal JAR）**架构，三大平台各仅发布 1 个 JAR，覆盖该平台全部支持的 MC 版本。共 3 个发布 JAR，替代 v1.2.1 的 7 个分组 JAR。

| 平台 | MC 版本范围 | 覆盖版本数 | Java 版本 | 推荐下载的 JAR |
|------|------------|-----------|----------|---------------|
| Bukkit/Spigot/Paper | 1.7.10 ~ 1.21.3（全版本） | 59 | Java 8 / Java 17（按 MC 版本自动选择） | `HardwareBenchmark-2.0.0-bukkit-universal.jar` |
| Fabric | 1.14 ~ 1.21.3（全 Fabric 支持版本） | 全部版本 | Java 8 / Java 17（按 MC 版本自动选择） | `HardwareBenchmark-2.0.0-fabric-universal.jar` |
| Forge | 1.7.10 ~ 1.21.3（含 NeoForge 1.20.2+） | 59 | Java 8 / Java 17（按 MC 版本自动选择） | `HardwareBenchmark-2.0.0-forge-universal.jar` |

> 💡 **通用 JAR 实现机制**：
> - **Bukkit**：MRJAR（Multi-Release JAR）+ 运行时反射版本检测，Java 8 与 Java 17 字节码共存在同一 JAR 中，按运行环境自动加载对应版本类。
> - **Fabric**：基于 Fabric Intermediary 映射的反射式跨版本兼容，单一 JAR 覆盖 1.14 ~ 1.21.3 全部 Fabric 支持版本。
> - **Forge**：容器 JAR（Container JAR）方案，内嵌 4 个子 JAR 于 `META-INF/jars/`，运行时按 Forge 版本选择入口类：
>   - `ForgeEntryLegacy`：Forge 1.7.10 / 1.12.2（`cpw.mods.fml`）
>   - `ForgeEntryClassic`：Forge 1.12.2 ~ 1.20.1（`net.minecraftforge.fml`）
>   - `ForgeEntryNeo`：NeoForge 1.20.2+（`net.neoforged.fml`）

> 📌 **回滚保留规则**：发布新版本时，上一代 JAR 文件会保留在 Release 资产中，文件名添加 `-prev` 后缀（如 `HardwareBenchmark-1.2.1-bukkit-java8-prev.jar`），供用户在遇到问题时回滚对比。

📦 [前往 Release 页面下载 JAR](https://github.com/waiyi233ye/HardwareBenchmark/releases/tag/v2.0.0)

## 安装

> 💡 **v2.0.0 简化**：每个加载器只需下载 **1 个** Universal JAR，无需再根据 MC 版本 / Java 版本挑选 7 个分组 JAR 中的某一个（v1.2.1 时代）。一个平台，一个 JAR，覆盖该平台全部支持的 MC 版本。

1. 根据上表选择对应平台的 **唯一一个** JAR 文件（Bukkit / Fabric / Forge 三选一）
2. Bukkit/Spigot/Paper：将 JAR 放入服务器的 `plugins/` 目录
3. Fabric：将 JAR 放入服务器的 `mods/` 目录（需安装 Fabric API）
4. Forge：将 JAR 放入服务器的 `mods/` 目录
5. 重启服务器

> ↩️ **回滚**：如遇问题，可从同一 Release 页面下载 `-prev` 后缀的 v1.2.1 JAR（如 `HardwareBenchmark-1.2.1-bukkit-java8-prev.jar`）替换回滚，与 v2.0.0 共存于同一 Release 资产中。

## 命令

所有命令都需要管理员权限（OP 或权限等级 2+）：

| 命令 | 说明 |
|------|------|
| `/hwbench` | 显示帮助 |
| `/hwbench help` | 显示帮助 |
| `/hwbench detect` | 检测硬件信息 |
| `/hwbench cpu` | CPU 跑分 |
| `/hwbench mem` | 内存跑分 |
| `/hwbench disk` | 磁盘跑分 |
| `/hwbench all` | 运行全部跑分 |
| `/hwbench libs` | 检查并补全 Linux 运行库 |
| `/hwbench lock` | 手动锁定服务器 |
| `/hwbench unlock` | 手动解锁服务器 |

## 项目结构

```
HardwareBenchmark/
├── common/              # 通用核心代码（硬件检测、跑分引擎、库管理）
├── bukkit/              # Bukkit/Spigot/Paper 平台实现（1.7.10~1.21.3，MRJAR）
├── fabric/              # Fabric 平台实现（1.14~1.21.3，反射跨版本）
├── forge-container/     # Forge 通用容器 JAR（v2.0.0 新增，外层调度器 + 内嵌 4 个子 JAR）
├── forge/               # Forge 1.18+ 子 JAR（容器内嵌，非独立发布）
├── forge-1.7.10/        # Forge 1.7.10 子 JAR（容器内嵌，非独立发布）
├── forge-1.12.2/        # Forge 1.12.2 子 JAR（容器内嵌，非独立发布）
├── forge-1.16.5/        # Forge 1.16.5 子 JAR（容器内嵌，非独立发布）
├── proc-stub/           # /proc 硬件检测实现（Forge 1.18+ 使用，不依赖 OSHI/JNA）
├── pom.xml              # Maven 父 POM
└── .github/             # Issue 模板
```

## 技术特性

- **通用 JAR 跨版本兼容**：v2.0.0 起三大平台各仅 1 个 JAR，覆盖 1.7.10 ~ 1.21.3 共 59 个 MC 版本
  - **Bukkit**：MRJAR（Multi-Release JAR）+ 运行时反射版本检测
  - **Fabric**：基于 Fabric Intermediary 映射的反射式跨版本兼容
  - **Forge**：容器 JAR（Container JAR），内嵌 4 个子 JAR 于 `META-INF/jars/`，按版本选择入口类（`ForgeEntryLegacy` / `ForgeEntryClassic` / `ForgeEntryNeo`）
- **OSHI 硬件检测**：跨平台硬件信息获取（含降级容错）
- **异步跑分**：跑分在后台线程执行，不阻塞主线程
- **统一命令接口**：三大平台使用相同的 `/hwbench` 命令

## 许可证

MIT License

## 反馈

发现问题请提交 [Issue](https://github.com/waiyi233ye/HardwareBenchmark/issues)。
