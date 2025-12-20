#!/usr/bin/env python3
import sys
import re

# -----------------------------
# ISA definition
# -----------------------------

OPCODES = {
    "add":   0x0,
    "sub":   0x1,
    "addi":  0x2,
    "loadi": 0x3,
    "beqz":  0x4,
    "bnez":  0x5,
    "bltz":  0x6,
    "bgtz":  0x7,
    "jump":  0x8,
    "ldcmd": 0x9,
    "blit":  0xA,
    "mul" :  0xB,
}

REGISTER_RE = re.compile(r"r([0-9]|[12][0-9]|3[01])$")

# -----------------------------
# Utilities
# -----------------------------

def parse_int(tok, consts):
    if tok in consts:
        return consts[tok]
    if tok.startswith("0x"):
        return int(tok, 16)
    return int(tok, 10)

def check_range(val, bits, signed):
    if signed:
        minv = -(1 << (bits - 1))
        maxv = (1 << (bits - 1)) - 1
    else:
        minv = 0
        maxv = (1 << bits) - 1
    if not (minv <= val <= maxv):
        raise ValueError(f"value {val} does not fit in {bits}-bit "
                         f"{'signed' if signed else 'unsigned'} field")
    return val & ((1 << bits) - 1)

def parse_reg(tok):
    m = REGISTER_RE.match(tok)
    if not m:
        raise ValueError(f"invalid register '{tok}'")
    return int(m.group(1))

# -----------------------------
# First pass: labels & layout
# -----------------------------

def first_pass(lines):
    pc = 0
    labels = {}
    consts = {}

    for lineno, line in enumerate(lines, 1):
        line = line.split(";")[0].strip()
        if not line:
            continue

        if line.startswith(".const"):
            _, name, value = line.split()
            consts[name] = int(value, 0)
            continue

        if line.startswith(".org"):
            _, addr = line.split()
            pc = int(addr, 0)
            continue

        if line.endswith(":"):
            label = line[:-1]
            if label in labels:
                raise ValueError(f"duplicate label '{label}' (line {lineno})")
            labels[label] = pc
            continue

        pc += 1
        if pc >= 1024:
            raise ValueError("ROM overflow (>1024 instructions)")

    return labels, consts

# -----------------------------
# Second pass: encode
# -----------------------------

def encode(lines, labels, consts):
    rom = {}
    pc = 0

    for lineno, line in enumerate(lines, 1):
        raw = line
        line = line.split(";")[0].strip()
        if not line:
            continue

        if line.startswith(".const"):
            continue

        if line.startswith(".org"):
            _, addr = line.split()
            pc = int(addr, 0)
            continue

        if line.endswith(":"):
            continue

        parts = re.split(r"[,\s]+", line)
        op = parts[0].lower()

        if op not in OPCODES:
            raise ValueError(f"unknown opcode '{op}' (line {lineno})")

        opcode = OPCODES[op]
        rd = rs1 = rs2 = 0

        try:
            if op in ("add", "sub", "mul"):
                rd  = parse_reg(parts[1])
                rs1 = parse_reg(parts[2])
                rs2 = parse_reg(parts[3])

            elif op == "addi":
                rd  = parse_reg(parts[1])
                rs1 = parse_reg(parts[2])
                imm = check_range(parse_int(parts[3], consts), 5, signed=True)
                rs2 = imm

            elif op == "loadi":
                rd  = parse_reg(parts[1])
                imm = check_range(parse_int(parts[2], consts), 10, signed=True)
                rs1 = (imm >> 5) & 0x1F
                rs2 = imm & 0x1F

            elif op in ("beqz", "bnez", "bltz", "bgtz"):
                rs1 = parse_reg(parts[1])
                target = parts[2]
                if target not in labels:
                    raise ValueError(f"unknown label '{target}'")
                addr = check_range(labels[target], 10, signed=False)
                rd  = (addr >> 5) & 0x1F
                rs2 = addr & 0x1F

            elif op == "jump":
                target = parts[1]
                if target not in labels:
                    raise ValueError(f"unknown label '{target}'")
                addr = check_range(labels[target], 10, signed=False)
                rd  = (addr >> 5) & 0x1F
                rs2 = addr & 0x1F

            elif op == "ldcmd":
                rd = parse_reg(parts[1])

            elif op == "blit":
                imm = check_range(parse_int(parts[1], consts), 10, signed=False)
                rs1 = (imm >> 5) & 0x1F
                rs2 = imm & 0x1F

        except IndexError:
            raise ValueError(f"wrong operand count (line {lineno})")

        instr = (opcode << 15) | (rd << 10) | (rs1 << 5) | rs2
        rom[pc] = instr
        pc += 1

    return rom

# -----------------------------
# Main
# -----------------------------

def main():
    if len(sys.argv) != 3:
        print("usage: blit_asm.py input.asm output.mem")
        sys.exit(1)

    with open(sys.argv[1]) as f:
        lines = f.readlines()

    labels, consts = first_pass(lines)
    rom = encode(lines, labels, consts)

    with open(sys.argv[2], "w") as f:
        for addr in range(1024):
            word = rom.get(addr, 0)
            f.write(f"{word:05X}\n")

    print("Assembled OK")

if __name__ == "__main__":
    main()
