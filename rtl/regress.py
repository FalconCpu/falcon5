import os
import subprocess
import re
import argparse
from collections import defaultdict

# Color escape codes
RED = "\033[1;31m"
GREEN = "\033[1;32m"
RESET = "\033[0m"

# Regex for register writes
line_re = re.compile(r"\$\s*(\d+)\s*=\s*([0-9a-fA-F]+)")

def parse_writes(filename):
    """Parse a register write log into {reg: [values in order]}"""
    reg_writes = defaultdict(list)
    with open(filename) as f:
        for line in f:
            m = line_re.search(line)
            if m:
                reg = int(m.group(1))
                val = int(m.group(2), 16)
                reg_writes[reg].append(val)
    return reg_writes

def compare_traces(golden_file, rtl_file, verbose=True):
    """Compare two register write logs."""
    golden = parse_writes(golden_file)
    rtl = parse_writes(rtl_file)

    all_regs = sorted(set(golden.keys()) | set(rtl.keys()))
    mismatches = []

    for r in all_regs:
        g_seq = golden.get(r, [])
        r_seq = rtl.get(r, [])
        if g_seq != r_seq:
            mismatches.append((r, g_seq, r_seq))
            if verbose:
                print(f"  Register ${r}:")
                print(f"    Golden: {[f'0x{v:08x}' for v in g_seq]}")
                print(f"    RTL:    {[f'0x{v:08x}' for v in r_seq]}")

    return mismatches

def run_simulation(test_file, verbose):
    """Run assembler, RTL simulation, and CPU emulator for a test file."""
    base_name = os.path.splitext(os.path.basename(test_file))[0]

    # Run assembler
    result = subprocess.run(["f32asm.exe", test_file])
    if result.returncode != 0:
        print(f"{test_file} {RED}FAIL ASM{RESET}")
        return False

    # Run RTL simulation
    vvplog = open("vvp.log","w")
    try:
        subprocess.run(["vvp.exe", "a.out"], check=True, stdout=vvplog, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError:
        print(f"{test_file} {RED}FAIL RTL{RESET}")
        return False

    # Run CPU emulation
    simlog = open("sim.log","w")
    try:
        subprocess.run(["f32sim.exe", "asm.hex"], check=True, stdout=simlog, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError:
        print(f"{test_file} {RED}FAIL SIM{RESET}")
        return False

    # Compare logs
    mismatches = compare_traces("sim_reg.log", "rtl_reg.log", verbose)
    if mismatches:
        print(f"{test_file} {RED}FAIL{RESET}")
        return False
    else:
        print(f"{test_file} {GREEN}PASS{RESET}")
        return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run CPU model regression tests.")
    parser.add_argument("test_file", nargs="?", type=str, help="Path to a specific test case file (.f32).")
    args = parser.parse_args()

    all_pass = True

    if args.test_file:
        if not run_simulation(args.test_file, verbose=True):
            all_pass = False
    else:
        test_dir = "testcases"
        for filename in sorted(os.listdir(test_dir)):
            if filename.endswith(".f32"):
                test_file = os.path.join(test_dir, filename)
                if not run_simulation(test_file, verbose=False):
                    all_pass = False