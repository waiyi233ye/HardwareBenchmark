#!/usr/bin/env python3
"""
通用 JAR 加载验证脚本。
用法: python3 test_universal.py <case_name> <server_dir> <java_home> <startup_cmd> <server_port> <rcon_port> <jar_install_path> <universal_jar> [<extra_test_cmd>]

启动服务器 -> 等待 Done -> RCON hwbench(帮助) + hwbench cpu(跑分) -> 捕获结果 -> stop -> 恢复原 jar
"""
import os, re, shutil, socket, struct, subprocess, sys, time
from pathlib import Path

RCON_PASS = "hwbench123"

def log(m): print(f"[{sys.argv[1]}] {m}", flush=True)

def make_packet(req_id, ptype, payload):
    pb = payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", 4+4+len(pb), req_id, ptype) + pb

def recv_packet(s):
    ld = s.recv(4)
    if len(ld) < 4: return None, None, b""
    length = struct.unpack("<i", ld)[0]
    data = b""
    while len(data) < length:
        c = s.recv(length - len(data))
        if not c: break
        data += c
    return struct.unpack("<i", data[0:4])[0], struct.unpack("<i", data[4:8])[0], data[8:-2]

def rcon(cmd, host="127.0.0.1", port=25575, timeout=60, short=2.0):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    try:
        s.connect((host, port))
        s.sendall(make_packet(1, 3, RCON_PASS))
        rid, _, _ = recv_packet(s)
        if rid == -1: return "[AUTH FAILED]"
        s.sendall(make_packet(2, 2, cmd))
        chunks = []
        deadline = time.time() + timeout
        s.settimeout(short)
        while time.time() < deadline:
            try:
                rid, ptype, payload = recv_packet(s)
                if payload is None: break
                chunks.append(payload)
            except socket.timeout:
                break
        return b"".join(chunks).decode("utf-8", errors="replace")
    finally:
        s.close()

def configure(d, sport, rport):
    sp = Path(d) / "server.properties"
    if not sp.exists(): return False
    keys = {"enable-rcon":"true","rcon.password":RCON_PASS,"rcon.port":str(rport),
            "server-port":str(sport),"online-mode":"false","level-type":"FLAT",
            "max-players":"1","view-distance":"3","spawn-monsters":"false",
            "spawn-animals":"false","spawn-npcs":"false","generate-structures":"false",
            "allow-nether":"false","snooper-enabled":"false"}
    seen=set(); out=[]
    for ln in sp.read_text(encoding="utf-8",errors="replace").splitlines():
        m=re.match(r"^([a-z\-\._]+)=.*$", ln)
        if m and m.group(1) in keys:
            out.append(f"{m.group(1)}={keys[m.group(1)]}"); seen.add(m.group(1))
        else: out.append(ln)
    for k,v in keys.items():
        if k not in seen: out.append(f"{k}={v}")
    sp.write_text("\n".join(out)+"\n", encoding="utf-8")
    return True

def wait_done(d, timeout=240):
    lf = Path(d)/"logs"/"latest.log"
    start=time.time()
    while time.time()-start < timeout:
        if lf.exists():
            try: txt=lf.read_text(encoding="utf-8",errors="replace")
            except: txt=""
            if "Done (" in txt and "For help" in txt: return True
            if "FAILED TO START" in txt or "Failed to start" in txt: return False
            # Fabric/Forge may crash early
            if "Stopping server" in txt and "Done (" not in txt and time.time()-start>30: pass
        time.sleep(1.0)
    return False

def main():
    (name, d, jhome, cmd, sport, rport, jar_path, univ_jar) = sys.argv[1:9]
    extra = sys.argv[9] if len(sys.argv) > 9 else None
    d = Path(d); jar_path = Path(jar_path); univ_jar = Path(univ_jar)
    java = f"{jhome}/bin/java"

    # backup original jar, install universal
    bak = jar_path.with_suffix(jar_path.suffix + ".orig")
    if not bak.exists():
        shutil.copy2(jar_path, bak)
    shutil.copy2(univ_jar, jar_path)
    log(f"installed universal jar -> {jar_path.name}")

    # truncate logs
    lf = d/"logs"/"latest.log"
    if lf.exists():
        try: lf.write_text("", encoding="utf-8")
        except: pass

    configure(d, sport, rport)

    # start server
    log(f"starting: {cmd}")
    env = os.environ.copy()
    env["JAVA_HOME"] = jhome
    env["PATH"] = f"{jhome}/bin:" + env.get("PATH","")
    proc = subprocess.Popen(cmd, shell=True, cwd=str(d), env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    ok = wait_done(d, timeout=240)
    if not ok:
        log("SERVER FAILED TO START")
        # dump tail of log
        try:
            tail = lf.read_text(encoding="utf-8",errors="replace")[-2000:]
            print("--- log tail ---"); print(tail)
        except: pass
        proc.kill()
        # restore
        shutil.copy2(bak, jar_path)
        log("restored original jar")
        print("RESULT: FAIL (server did not start with universal jar)")
        return

    log("server started, sending RCON commands")
    # 1. help
    help_resp = rcon("hwbench", port=rport, timeout=10)
    log(f"hwbench help response: {repr(help_resp[:200])}")

    # 2. cpu benchmark (real test)
    cpu_resp = rcon("hwbench cpu", port=rport, timeout=15)
    log(f"hwbench cpu ack: {repr(cpu_resp[:200])}")
    # wait for completion in log
    markers = ["CPU跑分完成","跑分完成","跑分失败","检测失败","Exception","Error"]
    deadline = time.time() + 120
    found = None
    while time.time() < deadline:
        try: txt = lf.read_text(encoding="utf-8",errors="replace")
        except: txt=""
        for m in markers:
            if m in txt:
                found = m; break
        if found: break
        time.sleep(1.0)

    # capture evidence lines
    try:
        full = lf.read_text(encoding="utf-8",errors="replace")
    except: full=""
    # find plugin load + score lines
    load_lines = [l for l in full.splitlines() if "HardwareBenchmark" in l or "hwbench" in l.lower()][:8]
    score_lines = [l for l in full.splitlines() if "跑分完成" in l or "得分" in l or "Exception" in l or "Error" in l][:12]

    # optional extra command
    extra_resp = ""
    if extra:
        extra_resp = rcon(extra, port=rport, timeout=15)
        log(f"extra cmd '{extra}' resp: {repr(extra_resp[:200])}")

    # stop
    try: rcon("stop", port=rport, timeout=10)
    except: pass
    try: proc.wait(timeout=30)
    except: proc.kill()

    # restore original jar
    shutil.copy2(bak, jar_path)
    log("restored original jar")

    print("\n=== PLUGIN LOAD LINES ===")
    for l in load_lines: print(l)
    print("\n=== SCORE/RESULT LINES ===")
    for l in score_lines: print(l)
    print(f"\nfound marker: {found}")

    help_ok = "HardwareBenchmark" in help_resp or "hwbench" in help_resp.lower()
    score_ok = found in ("CPU跑分完成","跑分完成")
    verdict = "PASS" if (ok and help_ok and score_ok) else "FAIL"
    print(f"\nRESULT: {verdict} (started={ok}, help_ok={help_ok}, score_ok={score_ok})")

if __name__ == "__main__":
    main()
