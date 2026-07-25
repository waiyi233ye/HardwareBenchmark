# HardwareBenchmark

> MC Java 版服务端硬件检测与跑分插件（Bukkit / Fabric / Forge，兼容 1.7.10 ~ 1.20.1）

## 简介

HardwareBenchmark 是一款 Minecraft Java 版服务端插件，用于检测服务器硬件信息并执行 CPU/内存/磁盘跑分。支持三大主流模组加载器（Bukkit/Spigot/Paper、Fabric、Forge），覆盖从 1.7.10 到 1.20.1 的全部主流版本。

### 创作背景

这是一个因为租赁面板服一直卡顿给卡烦而使用 AI 编写的检验性能及硬件的插件。作者在长期受困于面板服卡顿、却无法判断是商家超售还是自身配置不足之后，借助 AI 辅助开发完成了这套可在服务端直接跑分的工具，希望帮助同样遭遇的服主快速定位问题根源。

### AI 编写声明

本项目的代码、文档与构建脚本均由 AI 辅助生成（人类负责需求设计、测试验证与发布维护）。请在使用前自行审查代码、确认其行为符合预期；作者不对插件在特定环境下的运行结果作任何担保。

## 功能

- 🔍 **硬件检测**：基于 OSHI 库，检测 CPU 型号、内存容量、磁盘信息、操作系统
- ⚡ **CPU 跑分**：素数计算 + 甜甜圈渲染，多线程测试
- 💾 **内存跑分**：大数组读写 + 内存拷贝带宽测试
- 💿 **磁盘跑分**：顺序/随机读写 IO 测试
- 🔒 **服务器锁定**：跑分期间可锁定服务器，踢出在线玩家并阻止新玩家加入
- 📦 **库检查**：自动检测并补全 Linux 运行所需系统库（lshw, lm-sensors, pciutils, smartmontools）

## 兼容版本矩阵

| 平台 | MC 版本 | Java 版本 | 推荐下载的 JAR |
|------|---------|----------|---------------|
| Bukkit/Spigot/Paper | 1.7.10, 1.12.2 | Java 8 | `HardwareBenchmark-1.2.0-bukkit-java8.jar` |
| Bukkit/Spigot/Paper | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 | `HardwareBenchmark-1.2.0-bukkit-java17.jar` |
| Fabric | 1.16.5, 1.18.2, 1.19.2, 1.20.1 | Java 17 | `HardwareBenchmark-1.2.0-fabric-universal.jar` |
| Forge | 1.7.10 | Java 8 | `HardwareBenchmark-1.2.0-forge-1.7.10.jar` |
| Forge | 1.12.2 | Java 8 | `HardwareBenchmark-1.2.0-forge-1.12.2.jar` |
| Forge | 1.16.5 | Java 8 | `HardwareBenchmark-1.2.0-forge-1.16.5.jar` |
| Forge | 1.18.2, 1.19.2, 1.20.1 | Java 17 | `HardwareBenchmark-1.2.0-forge-1.18plus.jar` |

> 💡 **JAR 合并说明**：采用通用 JAR 合并策略（按 Java 版本与平台分组），将 16 个版本专属 JAR 合并为 7 个发布 JAR，减少下载选择复杂度，同时保持跨版本兼容。

📦 [前往 Release 页面下载 JAR](https://github.com/waiyi233ye/HardwareBenchmark/releases/tag/v1.2.0)

## 安装

1. 根据上表选择对应的 JAR 文件
2. Bukkit/Spigot/Paper：将 JAR 放入服务器的 `plugins/` 目录
3. Fabric：将 JAR 放入服务器的 `mods/` 目录（需安装 Fabric API）
4. Forge：将 JAR 放入服务器的 `mods/` 目录
5. 重启服务器

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
├── bukkit/              # Bukkit/Spigot/Paper 平台实现（1.7.10~1.20.1）
├── fabric/              # Fabric 平台实现（1.16.5~1.20.1）
├── forge/               # Forge 1.18+ 平台实现
├── forge-1.7.10/        # Forge 1.7.10 专用实现
├── forge-1.12.2/        # Forge 1.12.2 专用实现
├── forge-1.16.5/        # Forge 1.16.5 专用实现
├── pom.xml              # Maven 父 POM
└── .github/             # Issue 模板
```

## 技术特性

- **跨版本兼容**：使用反射和条件类加载处理不同 MC 版本的 API 差异
- **OSHI 硬件检测**：跨平台硬件信息获取（含降级容错）
- **异步跑分**：跑分在后台线程执行，不阻塞主线程
- **统一命令接口**：三大平台使用相同的 `/hwbench` 命令

## 许可证

MIT License

## 反馈

发现问题请提交 [Issue](https://github.com/waiyi233ye/HardwareBenchmark/issues)。
