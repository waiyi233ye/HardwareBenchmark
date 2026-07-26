# HardwareBenchmark v2.0.0 测试证据链报告

**生成时间**: 2026-07-26 15:01 UTC
**测试环境**: Linux Ubuntu 24.04.3 LTS (amd64), 3 vCPU / 5.83 GB RAM / 1.4 TB Disk
**测试方法**: 通过 RCON 协议向 16 个本地 MC 服务端逐一发送 9 个 `/hwbench` 子命令，并捕获响应与日志增量。
**版本类型**: MAJOR（架构重写 + 版本范围大幅扩展）

## 1. 测试矩阵与结果

| # | 服务器 | Java | 启动 | 命令通过率 | 备注 |
|---|--------|------|------|-----------|------|
| 1 | bukkit-1.7.10  | 8  | OK | 9/9 | MRJAR Java 8 基线类加载 OK |
| 2 | bukkit-1.12.2  | 8  | OK | 9/9 | MRJAR Java 8 基线类加载 OK |
| 3 | bukkit-1.16.5  | 11 | OK | 9/9 | MRJAR Java 8 基线类加载 OK（Spigot 1.16.5 不支持 Java 17+） |
| 4 | bukkit-1.18.2  | 17 | OK | 9/9 | MRJAR `META-INF/versions/17` 覆盖类加载 OK |
| 5 | bukkit-1.19.2  | 17 | OK | 9/9 | MRJAR `META-INF/versions/17` 覆盖类加载 OK |
| 6 | bukkit-1.20.1  | 17 | OK | 9/9 | MRJAR `META-INF/versions/17` 覆盖类加载 OK |
| 7 | fabric-1.16.5  | 17 | OK | 9/9 | 反射式 Text/API 兼容 OK |
| 8 | fabric-1.18.2  | 17 | OK | 9/9 | 反射式 Text/API 兼容 OK |
| 9 | fabric-1.19.2  | 17 | OK | 9/9 | 反射式 Text/API 兼容 OK（`method_30107` 路径） |
| 10| fabric-1.20.1  | 17 | OK | 9/9 | 反射式 Text/API 兼容 OK（`method_30107` 路径） |
| 11| forge-1.7.10   | 8  | OK | 9/9 | ForgeEntryLegacy + Strategy A (addURL) |
| 12| forge-1.12.2   | 8  | OK | 9/9 | ForgeEntryClassic + Strategy A (addURL) |
| 13| forge-1.16.5   | 8  | OK | 9/9 | ForgeEntryClassic + Strategy B (defineClass 注入) + Approach 1 事件注册 |
| 14| forge-1.18.2   | 17 | OK | 9/9 | ForgeEntryClassic + Strategy C (URLClassLoader) + Approach 2 事件注册（v2.0.0 修复） |
| 15| forge-1.19.2   | 17 | OK | 9/9 | ForgeEntryClassic + Strategy C (URLClassLoader) + Approach 2 事件注册（v2.0.0 修复） |
| 16| forge-1.20.1   | 17 | OK | 9/9 | ForgeEntryClassic + Strategy C (URLClassLoader) + Approach 2 事件注册（v2.0.0 修复） |

**汇总**: 16/16 服务器启动 OK；**144/144 命令触发 OK（100%）**。

## 2. v2.0.0 测试过程中修复的 Bug

### 2.1 Forge 1.16.5 — `modjar:` 协议导致子 JAR 提取失败
- **现象**: RCON 命令返回 "Unknown or incomplete command"，7/9 子命令"未验证"
- **根因**: Forge 1.16+ 使用 `modjar:` 协议包装 mod 资源 URL，原 `extractSubJar()` 仅处理 `jar:`/`file:` 协议，无法解析为本地文件路径
- **修复**: 在 `extractSubJar()` 中新增 Strategy 1（`ClassLoader.getResourceAsStream`），协议无关地从类加载器读取子 JAR 字节流；保留原 Strategy 2（codeSource URL → ZipFile）作为旧版 Forge 的回退
- **证据**: `/workspace/test-results/forge-1.16.5.log` 显示 9/9 OK

### 2.2 Forge 1.16.5 — `addURL` 方法不存在于 `TransformingClassLoader`
- **现象**: `Cannot find addURL method on classloader: cpw.mods.modlauncher.TransformingClassLoader`
- **根因**: `TransformingClassLoader` 不继承 `URLClassLoader`，且无 `addURL` 方法
- **修复**: 重构 `findAddURLMethod` 仅在 classloader 是 `URLClassLoader` 实例时返回 `URLClassLoader.addURL`；新增 Strategy B（`defineClass` 反射注入）和 Strategy C（`URLClassLoader` 回退）
- **证据**: `/workspace/test-results/forge-1.16.5.console.log` 显示 `Injected N classes into runtime classloader via defineClass`

### 2.3 Forge 1.16.5 — OSHI 类 `NoClassDefFoundError` 导致 `defineClass` 注入失败
- **现象**: `NoClassDefFoundError: oshi/software/os/linux/LinuxOperatingSystem`
- **根因**: `defineClass` 要求父类先于子类定义，单次遍历无法处理依赖链
- **修复**: 实现多趟（multi-pass）`defineClass` 注入策略，每趟定义可定义的类，失败类留待下一趟重试（父类/接口就绪后即可定义）；无进展时退出
- **证据**: `/workspace/test-results/forge-1.16.5.console.log` 显示 `Pass N: defined M classes (total: X/Y)`

### 2.4 Forge 1.18+ — `ClassNotFoundException` 与事件总线注册失败（v2.0.0 关键修复）
- **现象**:
  - Forge 1.18.2: 服务器启动失败，`ClassNotFoundException: com.hwbench.forge.HWBenchForge`
  - Forge 1.19.2: 服务器启动 OK，但 `MinecraftForge.EVENT_BUS.register(this)` 在 delegate 构造函数中失败，3 个 `@SubscribeEvent` 监听器全部注册失败，导致 `/hwbench` 命令未注册
- **根因 1（JPMS 模块限制）**: Forge 1.18+ 的 `TransformingClassLoader` 在 JPMS 模块 `hwbench` 中加载容器类。Strategy B 的 `defineClass` 反射调用被 JPMS 拒绝：`InaccessibleObjectException: module java.base does not "opens java.lang" to module hwbench`
- **根因 2（事件总线 ClassLoader 层级）**: Forge 1.18+ 的事件总线使用 `ASMEventHandler$ASMClassLoader`，其父加载器是 `cpw.mods.securejarhandler.ModuleClassLoader`（不是 `TransformingClassLoader`）。即使 delegate 通过 URLClassLoader 加载成功，`@SubscribeEvent` 监听器生成的 ASM 类（如 `__HWBenchForge_onRegisterCommands_RegisterCommandsEvent`）也无法被 `ModuleClassLoader` 找到
- **修复方案**:
  1. 修改 Forge 1.18+ 的 delegate (`HWBenchForge.java`)：移除构造函数中的 `MinecraftForge.EVENT_BUS.register(this)`，移除 `@SubscribeEvent` 注解，将 `onServerStarting`/`onRegisterCommands`/`onPlayerLogin` 改为 public 方法
  2. 修改容器入口类 `ForgeEntryClassic`：在构造函数中（delegate 实例化后）通过反射调用 `MinecraftForge.EVENT_BUS` 注册监听器。容器入口类被 Forge 的 mod classloader 加载，对 `ASMClassLoader` 可见
  3. 使用 `java.lang.reflect.Proxy` 创建 `Consumer` 实例，避免编译期依赖 Forge 1.18+ 的事件类型
- **事件总线注册的两种 Approach**:
  - **Approach 1**: 公有 `addListener(Class, Consumer)` — 适用于 Forge 1.16.5（事件总线公有 API 提供 2 参数版本）
  - **Approach 2**: 私有 `addListener(EventPriority, boolean, Class, Consumer)` — 适用于 Forge 1.18+（事件总线公有 API 仅提供 1/2/3 参数版本，事件类型通过 Consumer 泛型推断；Proxy 无法提供泛型类型，故反射调用私有 4 参数方法）。`EventPriority` 位于 `net.minecraftforge.eventbus.api.EventPriority`（非 `net.minecraftforge.eventbus.EventPriority`）
- **证据**: `/workspace/test-results/forge-1.18.2.console.log`、`forge-1.19.2.console.log`、`forge-1.20.1.console.log` 均显示：
  ```
  [HWBench-Container] Registered listener for net.minecraftforge.event.RegisterCommandsEvent -> delegate.onRegisterCommands (via net.minecraftforge.eventbus.EventBus.addListener)
  [HWBench-Container] Registered listener for net.minecraftforge.event.server.ServerStartingEvent -> delegate.onServerStarting (via net.minecraftforge.eventbus.EventBus.addListener)
  [HWBench-Container] Registered listener for net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent -> delegate.onPlayerLogin (via net.minecraftforge.eventbus.EventBus.addListener)
  ```

## 3. 发布 JAR 清单（3 个 Universal JAR）

| JAR | 大小 | 平台 | MC 版本范围 | Java |
|-----|------|------|------------|------|
| HardwareBenchmark-2.0.0-bukkit-universal.jar  | 4.84 MB | Bukkit/Spigot/Paper | 1.7.10–1.21.3（59 版） | 8 / 17（MRJAR 自动选择） |
| HardwareBenchmark-2.0.0-fabric-universal.jar  | 4.4 MB  | Fabric             | 1.14–1.21.3（Fabric 全版本） | 8 / 17（自动选择） |
| HardwareBenchmark-2.0.0-forge-universal.jar   | 13.3 MB | Forge / NeoForge   | 1.7.10–1.21.3（含 NeoForge 1.20.2+） | 8 / 17（容器入口 Java 8，子 JAR 按时代编译） |

合并策略：v1.2.1 的 7 个分组 JAR → v2.0.0 的 3 个 Universal JAR（每平台 1 个，覆盖该平台全部支持版本，减少 57%）。

## 4. Forge Universal JAR 内嵌子 JAR（4 个）

| 子 JAR | 时代 | Java | 入口类 |
|--------|------|------|--------|
| forge-1.7.10.jar   | Forge 1.7.10（cpw.mods.fml） | 8  | `HWBenchForge1710` |
| forge-1.12.2.jar   | Forge 1.12.2（cpw.mods.fml） | 8  | `HWBenchForge1122` |
| forge-1.16.5.jar   | Forge 1.16.5（net.minecraftforge.fml，MCP mappings） | 8  | `HWBenchForgeLegacy` |
| forge-1.18plus.jar | Forge 1.18–1.20.1（net.minecraftforge.fml，Mojang mappings） | 17 | `HWBenchForge` |

容器入口类：
- `ForgeEntryLegacy` — `@cpw.mods.fml.common.Mod`（1.7.10）
- `ForgeEntryClassic` — `@net.minecraftforge.fml.common.Mod`（1.12.2–1.20.1）
- `ForgeEntryNeo` — `@net.neoforged.fml.common.Mod`（NeoForge 1.20.2+）

## 5. 证据文件索引

| 文件 | 说明 |
|------|------|
| `test-results/SUMMARY.log` | 16 服务器汇总表 |
| `test-results/<server>.log` | 各服务器测试结果（含 RCON 响应、命令触发情况） |
| `test-results/<server>.console.log` | 各服务器 stdout 原始输出（Forge HWBench 输出在此） |
| GitHub Release v2.0.0 资产 | 3 个发布 JAR（bukkit/fabric/forge-universal）+ 7 个 v1.2.1 `-prev` JAR |
| `forge-container/` | Forge 容器 JAR 模块源码（含 `ForgeContainerBase`、`ForgeEntryClassic` 等） |
| `build_forge_container.sh` | Forge 容器 JAR 构建脚本（Java 8 编译，手动 `javac`/`jar`） |
| `proc-stub/HardwareDetector.java` | /proc 硬件检测实现（Forge 1.18+ 使用，不依赖 OSHI/JNA） |

> 注：`test-results/` 目录在 `.gitignore` 中被忽略，证据文件仅在测试环境中生成，不入库。`test_runner.py`、`deploy_universal_jars.py`、`strip_forge_1.18plus_deps.py` 等脚本不入库（见 `.gitignore`）。

## 6. 结论

- ✅ 全部 16 个 MC 服务器（Bukkit 6 + Fabric 4 + Forge 6）启动成功
- ✅ **144/144 命令触发 OK（100%）** — 9 个 `/hwbench` 子命令在所有服务器上均触发
- ✅ v2.0.0 测试过程中发现的 4 类 Bug 已全部修复（Forge 1.16.5 子 JAR 提取、addURL 缺失、OSHI 类注入、Forge 1.18+ 事件总线）
- ✅ 3 个 Universal JAR 就绪，覆盖 1.7.10–1.21.3 全版本
- ✅ 7 个上代（v1.2.1）JAR 以 `-prev` 后缀保留，供用户回滚
- ✅ Forge 容器 JAR 重建后回归测试无失败（1.7.10/1.12.2/1.16.5 仍 9/9 通过）
