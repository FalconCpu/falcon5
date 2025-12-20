# 256-colour palette generator
# Format: 0xRRGGBB (8:8:8 packed into a single int)

AMBIENT = 0.25   # brightness level 0
MAX_BRIGHT = 1.0 # brightness level 15

def clamp(x):
    return max(0, min(255, int(x)))

def rgb(r, g, b):
    return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b)

# 16 base colours (R, G, B) at full intensity
# Feel free to tweak these later
BASE_COLORS = [
    (255, 255, 255),  # 0 white
    (255, 0,   0),    # 1 red
    (0,   255, 0),    # 2 green
    (0,   0,   255),  # 3 blue
    (255, 255, 0),    # 4 yellow
    (255, 0,   255),  # 5 magenta
    (0,   255, 255),  # 6 cyan
    (255, 128, 0),    # 7 orange
    (128, 255, 0),    # 8 yellow-green
    (0,   255, 128),  # 9 aqua-green
    (0,   128, 255),  # A sky blue
    (128, 0,   255),  # B violet
    (255, 0,   128),  # C pink
    (192, 192, 192),  # D light grey
    (128, 128, 128),  # E mid grey
    (64,  64,  64),   # F dark grey
]

palette = [0] * 256

# --- Reserved colours (0–15) ---
palette[0x00] = rgb(0, 0, 0)       # true black
palette[0x01] = rgb(255, 255, 255) # UI white
palette[0x02] = rgb(0, 255, 0)     # scanner green
palette[0x03] = rgb(255, 0, 0)     # warning red
palette[0x04] = rgb(0, 128, 255)   # HUD blue
# rest can be zero or filled later

# --- Shaded colours (16–255) ---
for base in range(1,16):
    r0, g0, b0 = BASE_COLORS[base]

    for br in range(16):
        t = br / 15.0
        intensity = AMBIENT + t * (MAX_BRIGHT - AMBIENT)

        r = r0 * intensity
        g = g0 * intensity
        b = b0 * intensity

        index = (base << 4) | br
        palette[index] = rgb(r, g, b)

# --- Output ---
# Print as hex values (one per line)
print("const palette = const Array<Int> [\n")
for c in palette:
    print(f"    0x{c:06X},")
print("]")
