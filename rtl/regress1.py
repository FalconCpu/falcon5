import os
import subprocess
import re
import argparse
from collections import defaultdict

# color escape codes
RED = "\033[1;31m"
GREEN = "\033[1;32m"
RESET = "\033[0m"  # Reset color

line_re = re.compile(r"\$\s*(\d+)\s*=\s*([0-9a-fA-F]+)")

def parse_writes(filename):
    reg_writes = defaultdict(list)
    with open(filename) as f:
        for line in f:
            m = line_re.search(line)
            if m:
                reg = int(m.group(1))
                val = int(m.group(2), 16)
                reg_writes[reg].append(val)
                # print(f"$reg $val\n")
    return reg_writes

def compare_traces(golden_file, rtl_file):
    print("here2\n")
    golden = parse_writes(golden_file)
    rtl = parse_writes(rtl_file)

    all_regs = sorted(set(golden.keys()) | set(rtl.keys()))
    mismatches = []

    for r in all_regs:
        g_seq = golden.get(r, [])
        r_seq = rtl.get(r, [])
        if g_seq != r_seq:
            mismatches.append((r, g_seq, r_seq))

    return mismatches



def run_simulation(test_file):
    """
    Runs the simulation and emulation for a given test case.

    Args:
    test_file: Path to the test case file (with suffix .f32).
    vvp_path: Path to the vvp.exe simulator executable.
    f32sim_path: Path to the f32sim.exe emulator program.

    """
    # Run the assembler
    vvp_cmd = ["f32asm.exe", test_file]
    result = subprocess.run(vvp_cmd)
    if result.returncode!=0:
        print(f"{test_file} {RED}FAIL ASM{RESET}")
        return

    # Run RTL simulation
    rtl_log = "rtl_reg.log"
    vvp_cmd = ["vvp.exe", "a.out"] 
    vvplog = open("vvp.log","w")
    subprocess.run(vvp_cmd, check=True, stdout=vvplog)

    # Run CPU emulation
    sim_log = "sim_reg.log"
    sim_cmd = ["f32sim.exe", "asm.hex"]
    subprocess.run(sim_cmd, check=True,  stdout=vvplog)

    print("here1\n")
    mismatch = compare_traces(sim_log, rtl_log)
    if mismatch:
        print(f"{test_file} {RED}FAIL{RESET}")
    else:
        print(f"{test_file} {GREEN}PASS{RESET}")


if __name__ == "__main__":

  # Define argument parser
  parser = argparse.ArgumentParser(description="Run CPU model regression tests.")
  parser.add_argument("test_file", nargs="?", type=str, help="Path to a specific test case file (.f32).")
  args = parser.parse_args()

  if args.test_file:
    run_simulation(args.test_file)    
  else:
    # Loop through all test case files
    test_dir = "testcases"
    for filename in os.listdir("testcases"):
        if filename.endswith(".f32"):
            test_file = os.path.join(test_dir, filename)
            run_simulation(test_file)