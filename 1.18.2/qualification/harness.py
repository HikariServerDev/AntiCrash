#!/usr/bin/env python3
"""Runtime qualification harness: starts the isolated test server and compares probe dumps.

Everything lives under build/runtime-qualification; nothing outside it is written.
"""
import os, re, shutil, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
Q = os.path.join(ROOT, "build", "runtime-qualification")
ENV = os.path.join(Q, "env")
PROBE = os.path.join(ENV, "probe")


def start(label, timeout=300):
    """Runs the server until it exits (script @halt/@stop). Returns (exit code, seconds)."""
    with open(os.path.join(PROBE, "label.txt"), "w") as f:
        f.write(label)
    log = open(os.path.join(ENV, "console-%s.log" % label), "w")
    began = time.time()
    proc = subprocess.Popen(
        ["systemd-run", "--user", "--scope", "-q", "-p", "MemoryMax=4G", "-p", "MemorySwapMax=0",
         "java", "-Xms1G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
        cwd=ENV, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
    try:
        code = proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
        code = "TIMEOUT"
    log.close()
    # The JVM can outlive `stop` (a non-daemon thread); make sure nothing is left behind.
    kill_leftovers()
    return code, time.time() - began


def kill_leftovers():
    """Kills java processes whose working directory is the test environment, and nothing else."""
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            if os.readlink("/proc/%s/cwd" % pid) != ENV:
                continue
            with open("/proc/%s/comm" % pid) as f:
                if f.read().strip() != "java":
                    continue
            os.kill(int(pid), 9)
        except OSError:
            pass


def script(lines):
    for name in ("script.txt", "script.abs.txt"):
        path = os.path.join(PROBE, name)
        if os.path.exists(path):
            os.remove(path)
    with open(os.path.join(PROBE, "script.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")


def clear_out():
    shutil.rmtree(os.path.join(PROBE, "out"), ignore_errors=True)


def dumps(label):
    out = {}
    directory = os.path.join(PROBE, "out", label)
    if not os.path.isdir(directory):
        return out
    for name in os.listdir(directory):
        with open(os.path.join(directory, name)) as f:
            out[int(name[:-4])] = f.read().split("\n")
    return out


def log_lines(label, pattern):
    with open(os.path.join(ENV, "console-%s.log" % label), errors="replace") as f:
        return [line.rstrip() for line in f if re.search(pattern, line)]


if __name__ == "__main__":
    print(start(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 300))
