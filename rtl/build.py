import subprocess
import re

# --- Configuration ---
command = ["iverilog.exe", "-g2012", "-I", ".\\src", "-s", "tb", ".\\src\\*.sv"]
pattern1 = re.compile(r"^.*constant selects in always.*$")
pattern2 = re.compile(r"^.*cannot be synthesized in an always_ff process.*$")
pattern3 = re.compile(r"^.*cannot be synthesized in an always_comb process.*$")

# --- Run the command ---
try:
    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,  # capture stderr too; change to subprocess.PIPE if you want to separate them
        text=True,
        check=False  # set to True to raise an error on non-zero exit codes
    )

    # --- Filter and print output ---
    for line in result.stdout.splitlines():
        if (not pattern1.search(line)) and (not pattern2.search(line) and (not pattern3.search(line))):
            print(line)

except FileNotFoundError:
    print(f"Error: Command not found: {command[0]}")
except Exception as e:
    print(f"An error occurred: {e}")
