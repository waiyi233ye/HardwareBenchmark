# HardwareBenchmark v1.2.0 - 首个公开发布版本

## 🎉 简介

HardwareBenchmark 是一款 Minecraft Java 版服务端硬件检测与跑分插件，支持 Bukkit/Spigot/Paper、Fabric、Forge 三大平台，兼容 1.7.10 ~ 1.20.1 全部主流版本。

## ✨ 功能

- 🔍 **硬件检测**：基于 OSHI 库，检测 CPU 型号、内存容量、磁盘信息、操作系统
- ⚡ **CPU 跑分**：素数计算 + 甜甜圈渲染，多线程测试
- 💾 **内存跑分**：大数组读写 + 内存拷贝带宽测试
- 💿 **磁盘跑分**：顺序/随机读写 IO 测试
- 🔒 **服务器锁定**：跑分期间可锁定服务器，踢出在线玩家并阻止新玩家加入
- 📦 **库检查**：自动检测并补全 Linux 运行所需系统库

## 📦 下载

本版本采用 **方案A（按 Java 版本 + 平台合并）**，将 16 个版本专属 JAR 合并为 7 个发布 JAR：

| JAR 文件 | 适用平台 | MC 版本 | Java 版本 |
|---------|---------|--------|----------|
| `HardwareBenchmark-1.2.0-bukkit-java8.jar` | Bukkit/Spigot/Paper | 1.7.10, 1.12.2 | Java 8 |
| `HardwareBenchmark-1.2.0-bukkit-java17.jar` | Bukkit/Spigot/Paper | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.0-fabric-universal.jar` | Fabric | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.0-forge-1.18plus.jar` | Forge | 1.18.2, 1.19.2, 1.20.1 | Java 17 |
| `HardwareBenchmark-1.2.0-forge-1.7.10.jar` | Forge | 1.7.10 | Java 8 |
| `HardwareBenchmark-1.2.0-forge-1.12.2.jar` | Forge | 1.12.2 | Java 8 |
| `HardwareBenchmark-1.2.0-forge-1.16.5.jar` | Forge | 1.16.5 | Java 8 |

## 📝 安装步骤

1. 根据上表选择对应的 JAR 文件下载
2. **Bukkit/Spigot/Paper**：将 JAR 放入服务器的 `plugins/` 目录
3. **Fabric**：将 JAR 放入服务器的 `mods/` 目录（需安装 Fabric API）
4. **Forge**：将 JAR 放入服务器的 `mods/` 目录
5. 重启服务器

## 🎮 命令

| 命令 | 说明 |
|------|------|
| `/hwbench` | 显示帮助 |
| `/hwbench detect` | 检测硬件信息 |
| `/hwbench cpu` | CPU 跑分 |
| `/hwbench mem` | 内存跑分 |
| `/hwbench disk` | 磁盘跑分 |
| `/hwbench all` | 运行全部跑分 |
| `/hwbench libs` | 检查并补全 Linux 运行库 |
| `/hwbench lock` | 手动锁定服务器 |
| `/hwbench unlock` | 手动解锁服务器 |

## 🔧 技术特性

- **跨版本兼容**：使用反射和条件类加载处理不同 MC 版本的 API 差异
- **JAR 合并策略**：16 个版本专属 JAR → 7 个发布 JAR（减少 56.25%）
- **OSHI 硬件检测**：跨平台硬件信息获取（含降级容错）
- **异步跑分**：跑分在后台线程执行，不阻塞主线程

## ✅ 测试验证

已在以下 16 个 MC 版本服务器上完成实测（通过 RCON 发送全部 9 个子命令验证）：
- Bukkit: 1.7.10, 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1
- Fabric: 1.16.5, 1.18.2, 1.19.2, 1.20.1
- Forge: 1.7.10, 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1

## 📄 许可证

MIT License
