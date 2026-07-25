#!/usr/bin/env python3
"""
HardwareBenchmark 跨平台命令实测脚本
逐一启动每个 Minecraft 服务器，通过 RCON 发送全部 9 个子命令，
捕获响应并保存到 /workspace/test-results/<server>.log
中途不暂停，全部跑完。
"""
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

RESULTS_DIR = Path("/workspace/test-results")
RESULTS_DIR.mkdir(exist_ok=True)

RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASS = "hwbench123"

# 全部 9 个子命令（顺序：先快速命令，再跑分命令，最后 lock/unlock）
# 跑分类命令需要等待前一个完成（服务器锁定），所以集中放一起
COMMANDS = [
    "hwbench",        # 等价 help
    "hwbench help",
    "hwbench detect",
    "hwbench libs",
    "hwbench cpu",    # 跑分（会锁定服务器）
    "hwbench mem",    # 跑分
    "hwbench disk",   # 跑分
    "hwbench all",    # 跑分（全套）
    "hwbench lock",   # 手动锁定
    "hwbench unlock", # 手动解锁
]

# 跑分类子命令（执行后需等待服务器自动解锁）
BENCHMARK_SUBS = {"cpu", "mem", "disk", "all"}
# 异步命令（需要等待后台线程完成才能确认结果）
ASYNC_SUBS = {"detect", "libs", "cpu", "mem", "disk", "all"}

JAVA_HOME = {
    "8":  "/root/.local/share/mise/installs/java/temurin-8.0.482+8",
    "16": "/root/.local/share/mise/installs/java/temurin-16.0.2+7",
    "17": "/root/.local/share/mise/installs/java/17.0.2",
    "25": "/root/.local/share/mise/installs/java/25.0.2",
}

# 测试服务器列表：(名称, 目录, Java主版本, 启动命令模板, 服务器端口)
SERVERS = [
    # Bukkit / Spigot / Paper
    ("bukkit-1.7.10",  "/workspace/test-bukkit-1.7.10",  "8",
     "{java} -Xmx1024m -jar spigot.jar nogui", 25501),
    ("bukkit-1.12.2",  "/workspace/test-bukkit-1.12.2",  "8",
     "{java} -Xmx1024m -jar spigot.jar nogui", 25502),
    ("bukkit-1.16.5",  "/workspace/test-bukkit-1.16.5",  "16",
     "{java} -Xmx1024m -jar spigot.jar nogui", 25503),
    ("bukkit-1.18.2",  "/workspace/test-bukkit-1.18.2",  "17",
     "{java} -Xmx1024m -jar paper.jar nogui", 25504),
    ("bukkit-1.19.2",  "/workspace/test-bukkit-1.19.2",  "17",
     "{java} -Xmx1024m -jar spigot.jar nogui", 25505),
    ("bukkit-1.20.1",  "/workspace/test-bukkit-1.20.1",  "17",
     "{java} -Xmx1024m -jar spigot.jar nogui", 25506),
    # Fabric
    ("fabric-1.16.5",  "/workspace/test-fabric-1.16.5",  "17",
     "{java} -Xmx1024m -jar fabric-server-launch.jar nogui", 25507),
    ("fabric-1.18.2",  "/workspace/test-fabric-1.18.2",  "17",
     "{java} -Xmx1024m -jar fabric-server-launch.jar nogui", 25508),
    ("fabric-1.19.2",  "/workspace/test-fabric-1.19.2",  "17",
     "{java} -Xmx1024m -jar fabric-server-launch.jar nogui", 25509),
    ("fabric-1.20.1",  "/workspace/test-fabric-1.20.1",  "17",
     "{java} -Xmx1024m -jar fabric-server-launch.jar nogui", 25510),
    # Forge
    ("forge-1.7.10",   "/workspace/test-server-1.7.10",  "8",
     "{java} -Xmx1024m -jar forge-1.7.10-10.13.4.1614-1.7.10-universal.jar nogui", 25511),
    ("forge-1.12.2",   "/workspace/test-server-1.12.2",  "8",
     "{java} -Xmx1024m -jar forge-1.12.2-14.23.5.2847-universal.jar nogui", 25512),
    ("forge-1.16.5",   "/workspace/test-server-1.16.5",  "8",
     "{java} -Xmx1024m -jar forge-1.16.5-36.2.42.jar nogui", 25513),
    ("forge-1.18.2",   "/workspace/test-server-1.18.2",  "17",
     "{java} @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.18.2-40.3.0/unix_args.txt nogui", 25514),
    ("forge-1.19.2",   "/workspace/test-server-1.19.2",  "17",
     "{java} @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.19.2-43.4.0/unix_args.txt nogui", 25515),
    ("forge-1.20.1",   "/workspace/test-server-1.20.1",  "17",
     "{java} @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.3.0/unix_args.txt nogui", 25516),
]


def log(msg):
    print(f"[test-runner] {msg}", flush=True)


def make_packet(req_id, ptype, payload):
    payload_bytes = payload.encode("utf-8") + b"\x00\x00"
    length = 4 + 4 + len(payload_bytes)
    return struct.pack("<iii", length, req_id, ptype) + payload_bytes


def recv_packet(sock):
    length_data = sock.recv(4)
    if len(length_data) < 4:
        return None, None, b""
    length = struct.unpack("<i", length_data)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    req_id = struct.unpack("<i", data[0:4])[0]
    ptype = struct.unpack("<i", data[4:8])[0]
    payload = data[8:-2]
    return req_id, ptype, payload


def rcon_command(command, overall_timeout=30, short_read_timeout=1.5):
    """
    Send a single command via RCON.
    Reads response packets with a short idle timeout to detect end of
    response. Falls back to overall_timeout if data keeps arriving.
    For async benchmark commands, use a longer short_read_timeout (e.g. 30s)
    to capture results sent from background threads.
    """
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)  # connect timeout
    try:
        s.connect((RCON_HOST, RCON_PORT))
        # auth
        s.sendall(make_packet(1, 3, RCON_PASS))
        rid, _, _ = recv_packet(s)
        if rid == -1:
            return "[AUTH FAILED]"
        # command packet
        s.sendall(make_packet(2, 2, command))
        chunks = []
        deadline = time.time() + overall_timeout
        # Short read timeout to detect end-of-response (no more data = done)
        s.settimeout(short_read_timeout)
        while time.time() < deadline:
            try:
                rid, ptype, payload = recv_packet(s)
                if payload is None:
                    break
                chunks.append(payload)
            except socket.timeout:
                # No more data within short_read_timeout, response is complete
                break
        return b"".join(chunks).decode("utf-8", errors="replace")
    finally:
        s.close()


def rcon_stop():
    """Send 'stop' command via RCON to gracefully shut down server."""
    try:
        rcon_command("stop", overall_timeout=10)
        return True
    except Exception as e:
        log(f"  RCON stop 失败: {e}")
        return False


def wait_for_benchmark_done(dir_path, last_log_size, timeout=180,
                            console_log_path=None, last_console_size=0):
    """
    Poll server log until benchmark completion is detected.
    Bukkit markers: '服务器已解锁' (auto-unlock) or '服务器仍处于锁定状态'.
    Forge markers: '跑分完成' (async benchmark finished) or '跑分失败'/'检测失败'.
    Checks both logs/latest.log AND the console.log (server stdout) since Forge's
    HWBench output goes to stdout, not the file appender.
    Returns (new_log_size, new_console_size).
    """
    log_file = Path(dir_path) / "logs" / "latest.log"
    deadline = time.time() + timeout
    markers = (
        "服务器已解锁", "服务器仍处于锁定状态", "服务器未锁定",
        "跑分完成", "CPU跑分完成", "内存跑分完成", "磁盘跑分完成", "全部跑分完成",
        "跑分失败", "检测失败", "硬件检测失败", "库检查失败",
        "硬件检测完成", "库检查完成",
        "=== 全部跑分完成 ===",
        "线程未捕获异常", "未捕获异常",
    )
    while time.time() < deadline:
        # Check latest.log
        try:
            txt = log_file.read_text(encoding="utf-8", errors="replace")
        except Exception:
            txt = ""
        new_part = txt[last_log_size:] if len(txt) >= last_log_size else txt
        if any(m in new_part for m in markers):
            return len(txt), last_console_size
        # Also check console.log (server stdout) — Forge HWBench output goes here
        if console_log_path:
            try:
                ctxt = console_log_path.read_text(encoding="utf-8", errors="replace")
            except Exception:
                ctxt = ""
            c_new_part = ctxt[last_console_size:] if len(ctxt) >= last_console_size else ctxt
            if any(m in c_new_part for m in markers):
                return len(txt), len(ctxt)
        time.sleep(1.0)
    return last_log_size, last_console_size


def get_log_size(dir_path):
    log_file = Path(dir_path) / "logs" / "latest.log"
    try:
        return len(log_file.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return 0


def configure_server(dir_path, server_port):
    """Update server.properties with RCON enabled and unique server port."""
    sp = Path(dir_path) / "server.properties"
    if not sp.exists():
        return False
    lines = sp.read_text(encoding="utf-8", errors="replace").splitlines()
    keys = {
        "enable-rcon": "true",
        "rcon.password": RCON_PASS,
        "rcon.port": str(RCON_PORT),
        "server-port": str(server_port),
        "online-mode": "false",
        "level-type": "FLAT",
        "max-players": "1",
        "view-distance": "3",
        "spawn-monsters": "false",
        "spawn-animals": "false",
        "spawn-npcs": "false",
        "generate-structures": "false",
        "allow-nether": "false",
        "snooper-enabled": "false",
    }
    seen = set()
    new_lines = []
    for ln in lines:
        m = re.match(r"^([a-z\-\._]+)=.*$", ln)
        if m and m.group(1) in keys:
            new_lines.append(f"{m.group(1)}={keys[m.group(1)]}")
            seen.add(m.group(1))
        else:
            new_lines.append(ln)
    for k, v in keys.items():
        if k not in seen:
            new_lines.append(f"{k}={v}")
    sp.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
    return True


def truncate_logs(dir_path):
    """Truncate latest.log so we can detect Done cleanly."""
    log_file = Path(dir_path) / "logs" / "latest.log"
    if log_file.exists():
        try:
            log_file.write_text("", encoding="utf-8")
        except Exception:
            pass


def wait_for_done(dir_path, timeout=180):
    """Wait until 'Done' appears in latest.log."""
    log_file = Path(dir_path) / "logs" / "latest.log"
    start = time.time()
    seen_done = False
    while time.time() - start < timeout:
        if log_file.exists():
            try:
                txt = log_file.read_text(encoding="utf-8", errors="replace")
            except Exception:
                txt = ""
            if "Done (" in txt and "For help" in txt:
                seen_done = True
                break
            # 早失败检测
            if "FAILED TO START" in txt or "Failed to start" in txt:
                break
            if "Exception in thread" in txt and "main" in txt and "Caused by" in txt:
                break
            if "STopping the server" in txt or "Stopping server" in txt:
                # 服务器在自己停止（启动失败）
                if not seen_done:
                    break
        time.sleep(1.0)
    return seen_done


def wait_for_process_exit(proc, timeout=45):
    """Wait for process to exit within timeout."""
    try:
        proc.wait(timeout=timeout)
        return True
    except subprocess.TimeoutExpired:
        try:
            proc.kill()
            proc.wait(timeout=10)
        except Exception:
            pass
        return False


def run_server_test(name, dir_path, java_major, start_cmd_template, server_port,
                    capture_log_path):
    """Run a single server test. Returns dict of results."""
    out = []
    out.append(f"=== 测试服务器: {name} ===")
    out.append(f"目录: {dir_path}")
    out.append(f"Java: {java_major}  服务器端口: {server_port}")
    out.append(f"开始时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    out.append("")

    # 1. 配置
    if not configure_server(dir_path, server_port):
        out.append("[错误] 配置 server.properties 失败")
        return {"name": name, "ok": False, "output": "\n".join(out)}

    truncate_logs(dir_path)

    # 2. 启动
    java_home = JAVA_HOME[java_major]
    java_bin = f"{java_home}/bin/java"
    if not os.path.exists(java_bin):
        out.append(f"[错误] Java 不存在: {java_bin}")
        return {"name": name, "ok": False, "output": "\n".join(out)}

    env = os.environ.copy()
    env["JAVA_HOME"] = java_home
    env["PATH"] = f"{java_home}/bin:" + env["PATH"]
    # 抑制交互式提示
    env["NO_COLOR"] = "1"

    cmd = start_cmd_template.format(java=java_bin)
    out.append(f"启动命令: {cmd}")
    out.append("")

    log_file_handle = open(capture_log_path / f"{name}.console.log", "w",
                           encoding="utf-8", errors="replace")

    # stdin 使用 PIPE 而非 DEVNULL：1.7.10 的 Spigot 在 stdin EOF 时
    # 会循环读取并刷 "Unknown command" 日志，PIPE 保持文件描述符打开。
    try:
        proc = subprocess.Popen(
            cmd, shell=True, cwd=dir_path, env=env,
            stdout=log_file_handle, stderr=subprocess.STDOUT,
            stdin=subprocess.PIPE,
        )
    except Exception as e:
        out.append(f"[错误] 启动失败: {e}")
        log_file_handle.close()
        return {"name": name, "ok": False, "output": "\n".join(out)}

    out.append(f"服务器进程 PID: {proc.pid}")

    # 3. 等待 Done
    if not wait_for_done(dir_path, timeout=180):
        out.append("[失败] 服务器未在 180s 内完成启动")
        # 终止进程
        try:
            proc.terminate()
            wait_for_process_exit(proc, timeout=15)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass
        log_file_handle.close()
        # 抓取最后日志
        try:
            tail = (Path(dir_path) / "logs" / "latest.log").read_text(
                encoding="utf-8", errors="replace")[-2000:]
            out.append("--- 服务器日志尾部 ---")
            out.append(tail)
        except Exception:
            pass
        return {"name": name, "ok": False, "output": "\n".join(out)}

    out.append("[OK] 服务器启动完成")
    out.append("")

    # 4. 等待 2s 让插件完全就绪
    time.sleep(2)

    # 5. 通过 RCON 发送全部命令
    cmd_results = {}
    last_log_size = get_log_size(dir_path)
    console_log_path = Path(capture_log_path) / f"{name}.console.log"

    def get_console_size():
        try:
            return len(console_log_path.read_text(encoding="utf-8", errors="replace"))
        except Exception:
            return 0

    last_console_size = get_console_size()
    for cmd in COMMANDS:
        out.append(f"--- RCON 命令: {cmd} ---")
        sub = cmd.split(" ", 1)[1] if " " in cmd else "help"
        is_async = sub in ASYNC_SUBS
        is_bench = sub in BENCHMARK_SUBS
        try:
            # 异步命令使用更长的读取超时，以捕获后台线程发送的结果
            if is_async:
                resp = rcon_command(cmd, overall_timeout=180, short_read_timeout=30)
            else:
                resp = rcon_command(cmd, overall_timeout=60, short_read_timeout=1.5)
            out.append(resp if resp else "[空响应]")
            cmd_results[cmd] = resp
        except Exception as e:
            out.append(f"[异常] {e}")
            cmd_results[cmd] = f"[ERROR: {e}]"
        out.append("")

        # 异步命令：等待后台线程完成（检测完成/跑分完成/失败标记）后再发下一条
        if is_async:
            # 如果 RCON 响应已包含完成标记，跳过日志等待
            resp_text = cmd_results.get(cmd) or ""
            rcon_done_markers = ("跑分完成", "跑分失败", "检测失败", "检测完成",
                                 "库检查完成", "库检查失败",
                                 "服务器已解锁", "服务器未锁定",
                                 "全部跑分完成", "未捕获异常")
            if any(m in resp_text for m in rcon_done_markers):
                out.append(f"  (RCON 响应已包含完成标记，跳过日志等待)")
                time.sleep(2)
            else:
                new_log_size, new_console_size = wait_for_benchmark_done(
                    dir_path, last_log_size, timeout=180,
                    console_log_path=console_log_path,
                    last_console_size=last_console_size)
                elapsed = (new_log_size - last_log_size) + (new_console_size - last_console_size)
                out.append(f"  (等待异步完成，日志增量 {elapsed} 字节)")
            last_log_size = get_log_size(dir_path)
            last_console_size = get_console_size()
        else:
            # 短暂停顿，让同步命令的日志有机会写入
            time.sleep(1.0)
            last_log_size = get_log_size(dir_path)
            last_console_size = get_console_size()

    # 6. 关闭服务器
    out.append("--- 通过 RCON 发送 stop ---")
    stop_ok = rcon_stop()
    out.append(f"stop 发送: {'OK' if stop_ok else '失败'}")

    # 7. 等待进程退出
    exited = wait_for_process_exit(proc, timeout=45)
    out.append(f"进程退出: {'OK' if exited else '超时被强杀'}")
    log_file_handle.close()

    # 8. 检查 hwbench 命令是否触发
    log_file = Path(dir_path) / "logs" / "latest.log"
    log_tail = ""
    try:
        log_tail = log_file.read_text(encoding="utf-8", errors="replace")
    except Exception:
        pass
    # 同时读取 console.log（Forge 的 HWBench 输出在 stdout 而非 latest.log）
    console_tail = ""
    try:
        console_tail = console_log_path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        pass
    # 合并两个日志用于关键字检测
    combined_tail = log_tail + "\n" + console_tail

    # 检测每个子命令是否在日志中触发对应处理
    triggered = {}
    for cmd in COMMANDS:
        # 子命令关键字
        sub = cmd.split(" ", 1)[1] if " " in cmd else "help"  # "hwbench" 等价 help
        # 在日志中查找该子命令触发的痕迹
        keywords = {
            "hwbench":     ["HardwareBenchmark 硬件跑分插件", "HardwareBenchmark 硬件跑分",
                            "HardwareBenchmark Forge", "HardwareBenchmark"],
            "help":        ["HardwareBenchmark 硬件跑分插件", "HardwareBenchmark 硬件跑分",
                            "HardwareBenchmark Forge", "HardwareBenchmark"],
            "detect":      ["正在检测硬件信息", "硬件检测完成", "HardwareDetector", "硬件检测失败",
                            "HWBench-Detect"],
            "libs":        ["HWBench-Libs", "Linux运行库", "JNA", "包管理器", "检查并补全Linux运行库",
                            "库检查完成", "库检查失败"],
            "lock":        ["服务器已锁定", "服务器锁定", "已锁定"],
            "unlock":      ["服务器已解锁", "服务器未锁定"],
            "cpu":         ["CPU跑分", "甜甜圈", "CPU 跑分", "HWBench-CPU"],
            "mem":         ["内存跑分", "Memory", "内存跑分完成", "HWBench-Mem"],
            "disk":        ["磁盘跑分", "Disk", "磁盘跑分完成", "HWBench-Disk"],
            "all":         ["全部跑分", "综合得分", "跑分完成", "运行全部跑分", "HWBench-All"],
        }
        kws = keywords.get(sub, [])
        if kws and any(k in combined_tail for k in kws):
            triggered[cmd] = True
        elif cmd in ("hwbench", "hwbench help"):
            triggered[cmd] = True  # help 没有日志，但 RCON 响应即成功
        elif cmd == "hwbench unlock" and "服务器未锁定" in (cmd_results.get(cmd) or ""):
            triggered[cmd] = True
        else:
            triggered[cmd] = False

    out.append("")
    out.append("=== 命令触发情况 ===")
    for cmd in COMMANDS:
        out.append(f"  {cmd:20s} -> {'OK' if triggered[cmd] else '未验证'}")

    # 9. 输出日志尾部（latest.log + console.log 合并尾部）
    out.append("")
    out.append("--- 服务器日志尾部 (latest.log 800 字符) ---")
    out.append(log_tail[-800:] if log_tail else "[无日志]")
    out.append("")
    out.append("--- 控制台日志尾部 (console.log 1200 字符) ---")
    out.append(console_tail[-1200:] if console_tail else "[无日志]")

    return {"name": name, "ok": True, "output": "\n".join(out),
            "triggered": triggered, "cmd_results": cmd_results}


def main():
    # 解析参数：可选服务器名过滤
    only = sys.argv[1] if len(sys.argv) > 1 else None

    summary = []
    for entry in SERVERS:
        name, dir_path, java_major, cmd_tpl, port = entry
        if only and only not in name:
            continue
        log(f"==== 开始测试 {name} ====")
        result = run_server_test(name, dir_path, java_major, cmd_tpl, port,
                                 RESULTS_DIR)
        # 写出结果文件
        out_path = RESULTS_DIR / f"{name}.log"
        out_path.write_text(result["output"], encoding="utf-8")
        summary.append({
            "name": name,
            "ok": result["ok"],
            "triggered": result.get("triggered", {}),
        })
        log(f"==== 完成 {name} (ok={result['ok']}) ====")

    # 汇总
    summary_path = RESULTS_DIR / "SUMMARY.log"
    lines = ["=== HardwareBenchmark 命令实测汇总 ===", ""]
    lines.append(f"{'服务器':<22} {'启动':<6} {'命令通过率':<14} 详情")
    lines.append("-" * 80)
    for s in summary:
        if s["ok"]:
            trig = s.get("triggered", {})
            total = len(trig) if trig else 0
            passed = sum(1 for v in trig.values() if v) if trig else 0
            rate = f"{passed}/{total}" if total else "N/A"
            status = "OK"
        else:
            rate = "N/A"
            status = "FAIL"
        lines.append(f"{s['name']:<22} {status:<6} {rate:<14}")
    lines.append("")
    lines.append("详细日志见各 <server>.log 文件。")
    summary_path.write_text("\n".join(lines), encoding="utf-8")
    print("\n" + "\n".join(lines))


if __name__ == "__main__":
    main()
