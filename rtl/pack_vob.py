import struct
import re

FIX_SCALE = 1 << 12

def to_fix12_12(x):
    v = int(round(float(x) * FIX_SCALE))
    if v < -0x800000 or v > 0x7FFFFF:
        raise ValueError(f"12.12 overflow: {x}")
    return v & 0xFFFFFF

def parse_vobs(filename):
    vobs = []
    cur = None

    with open(filename) as f:
        for line in f:
            # Remove comments (everything from '#' to end of line)
            if '#' in line:
                line = line[:line.index('#')]
            line = line.strip()
            if not line:
                continue

            if line.startswith("vob:"):
                if cur:
                    vobs.append(cur)
                cur = {}
            else:
                k, v = line.split(":")
                cur[k.strip()] = v.strip()

    if cur:
        vobs.append(cur)
    return vobs

def pack_vob(v):
    out = bytearray(64)

    def w16(ofs, val):
        struct.pack_into("<H", out, ofs, int(val) & 0xFFFF)

    def w32(ofs, val):
        struct.pack_into("<I", out, ofs, int(val) & 0xFFFFFFFF)

    def w24(ofs, val):
        out[ofs+2] = (val >> 16) & 0xFF
        out[ofs+1] = (val >> 8)  & 0xFF
        out[ofs+0] = val & 0xFF

    # --- Block 0 ---
    w16(0x00, v["ystart"])
    w16(0x02, v["yend"])
    w16(0x04, v["xclip1"])
    w16(0x06, v["xclip2"])
    w16(0x08, v["brk_y"])
    w16(0x0A, int(v["ctrl"], 0))
    w32(0x0C, int(v["src_stride"], 0))

    # --- Block 1 ---
    w24(0x10, to_fix12_12(v["x1"]))
    w24(0x13, to_fix12_12(v["x2"]))
    w24(0x16, to_fix12_12(v["z"]))
    w24(0x19, to_fix12_12(v["u"]))
    w24(0x1C, to_fix12_12(v["v"]))
    out[0x1F] = 0

    # --- Block 2 ---
    w32(0x20, int(v["src_addr"], 0))
    w24(0x24, to_fix12_12(v["dzdx"]))
    w24(0x27, to_fix12_12(v["dudx"]))
    w24(0x2A, to_fix12_12(v["dvdx"]))
    w24(0x2D, to_fix12_12(v["brk_dxdy"]))

    # --- Block 3 ---
    w24(0x30, to_fix12_12(v["dx1dy"]))
    w24(0x33, to_fix12_12(v["dx2dy"]))
    w24(0x36, to_fix12_12(v["dzdy"]))
    w24(0x39, to_fix12_12(v["dudy"]))
    w24(0x3C, to_fix12_12(v["dvdy"]))
    out[0x3F] = 0

    return out

def dump_hex_words(vobs, filename):
    with open(filename, "w") as f:
        for blob in vobs:
            for i in range(0, 64, 4):
                w = struct.unpack("I", blob[i:i+4])[0]
                f.write(f"{w:08X}\n")

if __name__ == "__main__":
    vobs = parse_vobs("vobs.txt")
    blobs = [pack_vob(v) for v in vobs]
    dump_hex_words(blobs, "vob_image.hex")
    print(f"Wrote {len(blobs)} VOBs")
