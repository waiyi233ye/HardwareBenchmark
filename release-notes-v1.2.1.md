# HardwareBenchmark v1.2.1 - 100% 命令通过率与版本规范化

## 🎉 简介

HardwareBenchmark 是一款 Minecraft Java 版服务端硬件检测与跑分插件，支持 Bukkit/Spigot/Paper、Fabric、Forge 三大平台，兼容 1.7.10 ~ 1.20.1 全部主流版本。

本版本在 v1.2.0 基础上修复了 Bukkit `detect`/`mem`/`cpu`/`disk` 命令的日志输出问题与内存跑分 OOM 崩溃，实现 **16 服务器 × 9 命令 = 144/144（100%）** 全量通过，并建立了版本号更新规范。

## ✨ 功能

- 🔍 **硬件检测**：检测 CPU 型号、内存容量、磁盘信息、操作系统
  - Bukkit / Fabric：基于 OSHI 库（跨平台）
  - Forge 1.18+：基于 Linux `/proc` 文件系统（避免 JPMS 分包冲突）
- ⚡ **CPU 跑分**：素数计算 + 甜甜圈渲染，多线程测试
- 💾 **内存跑分**：大数组读写 + 内存拷贝带宽测试
- 💿 **磁盘跑分**：顺序/随机读写 IO 测试
- 🔒 **服务器锁定**：跑分期间可锁定服务器，踢出在线玩家并阻止新玩家加入
- 📦 **库检查**：自动检测并补全 Linux 运行所需系统库（lshw, lm-sensors, pciutils, smartmontools）

## 📦 下载

采用**通用 JAR 合并策略**（按 Java 版本与平台分组），将 16 个版本专属 JAR 合并为 7 个发布 JAR：

| JAR 文件 | 适用平台 | MC 版本 | Java 版本 |
|---|---|---|---|
| `HardwareBenchmark-1.2.1-bukkit-java8.jar` | Bukkit/Spigot/Paper | 1.7.10, 1.12.2 | Java 8 |
| `HardwareBenchmark-1.2.1-bukkit-java17.jar` | Bukkit/Spigot/Paper | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.1-fabric-universal.jar` | Fabric | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.1-forge-1.18plus.jar` | Forge | 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.1-forge-1.7.10.jar` | Forge | 1.7.10 | Java 8 |
| `HardwareBenchmark-1.2.1-forge-1.12.2.jar` | Forge | 1.12.2 | Java 8 |
| `HardwareBenchmark-1.2.1-forge-1.16.5.jar` | Forge | 1.16.5 | Java 8 |

> 💡 文件名以 `-prev.jar` 结尾的是**上代 JAR**，保留供回滚对比；推荐下载不带 `-prev` 后缀的最新版本。

## 🔧 本版本修复的 Bug

### Bukkit `detect` 命令日志输出缺失（v1.2.0 遗留）
- **现象**：Bukkit 系列服务器执行 `/hwbench detect` 后，硬件检测结果仅通过 `sender.sendMessage()` 回传 RCON，未写入服务器日志，导致测试工具无法验证命令触发（通过率 8/9）
- **根因**：`BenchCommand.handleDetect` 异步块未调用 `plugin.getLogger().info()` 输出到控制台/日志文件
- **修复**：在 `handleDetect` 全路径添加 `plugin.getLogger().info("[HWBench-Detect] ...")` 日志输出，硬件检测进度与完整报告均同步写入服务器日志
- **效果**：6 个 Bukkit 服务器 `detect` 命令通过率从 0/6 提升至 6/6

### Bukkit `mem` 命令内存跑分静默崩溃（Paper 1.18.2+）
- **现象**：Paper 1.18.2/1.19.2/1.20.1 执行 `/hwbench mem` 后，跑分线程静默失败，无任何日志输出，测试检测为"未验证"（通过率 8/9）
- **根因**：
  1. 默认数组大小 256MB 在 1024m 堆上分配 2×256MB 数组时触发 `OutOfMemoryError`（`Error` 类型，不被 `catch (Exception)` 捕获，被 `CompletableFuture.runAsync` 静默吞没）
  2. 跑分进度仅通过 `sender.sendMessage()` 回传 RCON，未调用 `plugin.getLogger()` 写入日志文件
- **修复**：
  1. 将 `catch (Exception e)` 改为 `catch (Throwable e)`，捕获 `OutOfMemoryError` 并记录到日志
  2. 为 CPU/内存/磁盘跑分添加 `plugin.getLogger().info("[HWBench-{CPU|Mem|Disk}] ...")` 进度与完成日志
  3. 将默认数组大小从 256MB 降至 64MB、迭代次数从 10 降至 3（与 Forge 1.7.10 一致），避免堆内存不足
- **效果**：3 个 Paper 服务器 `mem` 命令通过率从 0/3 提升至 3/3，全平台命令通过率达 **144/144（100%）**

## 📝 安装步骤

1. 根据上表选择对应的 JAR 文件下载
2. **Bukkit/Spigot/Paper**：将 JAR 放入服务器的 `plugins/` 目录
3. **Fabric**：将 JAR 放入服务器的 `mods/` 目录（需安装 Fabric API）
4. **Forge**：将 JAR 放入服务器的 `mods/` 目录
5. 重启服务器

## 🎮 命令

| 命令 | 说明 |
|---|---|
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

## 🧪 测试验证

已在以下 16 个 MC 版本服务器上完成实机实测（通过 RCON 发送全部 9 个子命令验证）：

| 平台 | 版本 | 启动 | 命令通过率 |
|------|------|------|-----------|
| Bukkit | 1.7.10, 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1 | ✅ | 9/9 |
| Fabric | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | ✅ | 9/9 |
| Forge  | 1.7.10, 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1 | ✅ | 9/9 |

**汇总**：16/16 服务器启动 OK；**144/144 命令触发 OK（100%）**。完整测试报告见仓库 `docs/TEST_REPORT.md`。

## 🔧 技术特性

- **跨版本兼容**：使用反射和条件类加载处理不同 MC 版本的 API 差异
- **通用 JAR 合并**：16 个版本专属 JAR → 7 个发布 JAR（减少 56.25%），按 Java 版本与平台分组
- **多策略硬件检测**：Bukkit/Fabric 用 OSHI，Forge 1.18+ 用 /proc（避免模块冲突）
- **异步跑分**：跑分在后台线程执行，不阻塞主线程
- **统一命令接口**：三大平台使用相同的 `/hwbench` 命令
- **版本号规范**：建立 SemVer 版本号更新规则，写入项目必读文件 `.trae/rules/project_rules.md`

## 📄 许可证

MIT License
