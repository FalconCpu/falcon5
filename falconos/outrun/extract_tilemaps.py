import numpy as np
import matplotlib.pyplot as plt
from math import sqrt, ceil
from PIL import Image

# ----------------------------------------------------------
# Helpers for reading data
# ----------------------------------------------------------
def read16(data, addr):
    """Read 16-bit big-endian word and return (value, new_addr)."""
    val = (data[addr] << 8) | data[addr + 1]
    return val, addr + 2

def read8(data, addr):
    """Read 16-bit big-endian word and return (value, new_addr)."""
    val = data[addr]
    return val, addr + 1

def read32(data, addr):
    """Read 32-bit big-endian word and return (value, new_addr)."""
    val = (data[addr] << 24) | (data[addr + 1] << 16) | (data[addr + 2] << 8) | data[addr + 3]
    return val, addr + 4

def decompress_tilemap(data, src_addr, v_tiles):
    """Decompress an OutRun tilemap starting from src_addr."""
    decompressed = []
    for table_idx in range(4):  # Four 64x32 name tables per map
        table = []
        y = v_tiles - 1
        while y >= 0:
            row = []
            x = 0x3F  # 64 columns
            while x >= 0:
                data_word, src_addr = read16(data, src_addr)
                if data_word == 0:
                    value, src_addr = read16(data, src_addr)
                    count, src_addr = read16(data, src_addr)
                    for _ in range(count + 1):
                        row.append(value)
                        x -= 1
                        if x < 0:
                            break
                else:
                    row.append(data_word)
                    x -= 1
            table.insert(0, row)
            y -= 1
        decompressed.append(np.array(table, dtype=np.uint16))
    return decompressed

def read_tilemap_header(data, addr):
    """Read tilemap header and return fields."""
    fg_height, addr = read8(data, addr)
    bg_height, addr = read8(data, addr)
    fg_addr, addr = read32(data, addr)
    bg_addr, addr = read32(data, addr)
    scroll_offset, addr = read16(data, addr)
    palette, addr = read32(data, addr)
    return fg_height, bg_height, fg_addr, bg_addr, scroll_offset, palette


def convert_palette(pal_word):
    """Convert OutRun palette word to RGB tuple."""
    # D15 : Shade hi/lo
    # D14 : Blue bit 0
    # D13 : Green bit 0
    # D12 : Red bit 0
    # D11 : Blue bit 4
    # D10 : Blue bit 3
    #  D9 : Blue bit 2
    #  D8 : Blue bit 1
    #  D7 : Green bit 4
    #  D6 : Green bit 3
    #  D5 : Green bit 2
    #  D4 : Green bit 1
    #  D3 : Red bit 4
    #  D2 : Red bit 3
    #  D1 : Red bit 2
    #  D0 : Red bit 1

    r = (pal_word & 0x000f) << 1; # rrrr0
    g = (pal_word & 0x00f0) >> 3; # gggg0
    b = (pal_word & 0x0f00) >> 7; # bbbb0
    if ((pal_word & 0x1000) != 0):
        r |= 1
    if ((pal_word & 0x2000) != 0):
        g |= 1
    if ((pal_word & 0x4000) != 0):
        b |= 1
    return np.array([r, g, b])*8 # Scale 0-31 to 0-255

# ----------------------------------------------------------
# Load master CPU ROM
# ----------------------------------------------------------
rom_pairs = [
    ("epr-10380b.133", "epr-10382b.118"),  # lower half
    ("epr-10381b.132", "epr-10383b.117"),  # upper half
]
path = "../../../Downloads/outrun_amiga_edition_v092/in/"
rom = bytearray()

for even_path, odd_path in rom_pairs:
    with open(path+even_path, "rb") as f_even, open(path+odd_path, "rb") as f_odd:
        even = f_even.read()
        odd = f_odd.read()
        if len(even) != len(odd):
            raise ValueError("ROM size mismatch between %s and %s" % (even_path, odd_path))
        # Interleave even/odd bytes
        interleaved = bytearray()
        for e, o in zip(even, odd):
            interleaved += bytes([e, o])
        rom += interleaved

palettes = [
    0x170D0,
    0x170F0,
    0x17100,
    0x17110,
    0x17210,
    0x17220,
    0x17130,
    0x17150,
    0x17160,
    0x171F0,
    0x17230,
    0x17270,
    0xE0CC,
    0xE0BC,
    0xDFAC,
    0xDF9C,
    0xDFDC,
    0xE08C,
    0xE01C,
    0xDFCC,
    0xE0AC,
    0xE02C,
    0xDFEC,
    0xE04C,
    0xe06c]


def create_tile_image(tilemap_address, tilemap_height, palette_addr):
    # ----------------------------------------------------------
    # Load 4bpp tile graphics
    # ----------------------------------------------------------
    rom_path = "../../../Downloads/outrun_amiga_edition_v092/gfx/tiles.bin"
    with open(rom_path, "rb") as f:
        data = np.frombuffer(f.read(), dtype=np.uint8)

    pixels = np.zeros(len(data) * 2, dtype=np.uint8)
    pixels[0::2] = data >> 4        # high nibble
    pixels[1::2] = data & 0x0F      # low nibble

    TILE_W, TILE_H = 8, 8
    PIXELS_PER_TILE = TILE_W * TILE_H
    num_tiles = len(pixels) // PIXELS_PER_TILE
    pixels = pixels[:num_tiles * PIXELS_PER_TILE]
    tiles = pixels.reshape(num_tiles, TILE_H, TILE_W)


    print(f"Loaded {num_tiles} tiles from {rom_path}")

    # ----------------------------------------------------------
    # Load & decompress a tilemap from master CPU ROM
    # ----------------------------------------------------------
    # tilemap_data_offset = 0x30D80
    # fg_addr = tilemap_data_offset + 0x03DC  # Coconut Beach BG
    # fg_v_tiles = 0x12

    tilemap_parts = decompress_tilemap(rom, tilemap_address, tilemap_height)
    print(f"Decompressed {len(tilemap_parts)} subtilemaps")

    # ----------------------------------------------------------
    # Assemble the four 64x32 subtilemaps into a single map
    # (128x64 virtual name table)
    # ----------------------------------------------------------
    upper = np.hstack((tilemap_parts[2], tilemap_parts[1], tilemap_parts[0]))
    # lower = np.hstack((tilemap_parts[2], tilemap_parts[3]))
    tilemap_full = upper # np.vstack((upper, lower))

    print(f"Full tilemap shape: {tilemap_full.shape} (tiles)")

    # ----------------------------------------------------------
    # Extract the pallette indices from the tilemap entries
    # ----------------------------------------------------------

    palette_1 = [convert_palette(read16(rom, palette_addr + i*2)[0]) for i in range(16)]
    palette = np.array(palette_1, dtype=np.uint8)

    # --- Render the tilemap with color ---
    map_h, map_w = tilemap_full.shape
    TILE_H, TILE_W = 8, 8
    img = np.zeros((map_h * TILE_H, map_w * TILE_W), dtype=np.uint8)

    for ty in range(map_h):
        for tx in range(map_w):
            tile_index = tilemap_full[ty, tx]
            if tile_index < len(tiles):
                tile = tiles[tile_index]
                img[ty*TILE_H:(ty+1)*TILE_H, tx*TILE_W:(tx+1)*TILE_W] = tile
            else:
                img[ty*TILE_H:(ty+1)*TILE_H, tx*TILE_W:(tx+1)*TILE_W] = 0

    image = Image.fromarray(img)
    image.putpalette(palette.flatten().tolist())

    return image

# ----------------------------------------------------------
# Display
# ----------------------------------------------------------

# ----------------------------------------------------------
# Multi-palette visualiser
# ----------------------------------------------------------
def show_all_palettes(level_index, background, palettes):
    header = read_tilemap_header(rom, 0x17eac + 12 * level_index)
    rows = len(palettes)
    cols = 1

    plt.figure(figsize=(4 * cols, 3 * rows))
    for i, palette_addr in enumerate(palettes):
        img = create_tile_image(header[background+2], header[background], palette_addr)
        ax = plt.subplot(rows, cols, i + 1)
        plt.imshow(img, interpolation='nearest')
        # plt.title(f"{i}")
        plt.axis('off')

        # Add label on the right-hand side
        ax.text(
            1.02, 0.5,
            f"{i}",
            transform=ax.transAxes,
            fontsize=9,
            va='center',
            ha='left',
            color='white',
            bbox=dict(facecolor='black', alpha=0.6, boxstyle='round,pad=0.3')
        )

    plt.suptitle(f"Level {level_index} — All Palette Variants", fontsize=16)
    plt.tight_layout()
    plt.show()

def get_tilemap_image(level_index, background, palette_index):
    header = read_tilemap_header(rom, 0x17eac+12*level_index)         # Level 1 Coconut Beach

    print("Tilemap Header:")
    print(f"  FG Height: {header[0]:02X}")
    print(f"  BG Height: {header[1]:02X}")
    print(f"  FG Addr:   {header[2]:06X}")
    print(f"  BG Addr:   {header[3]:06X}")
    print(f"  Scroll:    {header[4]:04X}")
    print(f"  Palette:   {header[5]:08X}")  

    img = create_tile_image(tilemap_address=header[background+2], tilemap_height=header[background], palette_addr=palettes[palette_index])
    return img

def show_tilemap(level_index, background, palette_index):
    img = get_tilemap_image(level_index, background, palette_index)

    plt.figure(figsize=(16, 8))
    plt.imshow(img, interpolation='none')
    plt.axis('off')
    plt.title(f"Level {level_index} (Foreground Layer) palette index {palette_index}")
    plt.show()

# show_all_palettes(level_index=4, background=0, palettes=palettes)

# level bg palette
levels = [  (0,1,9),
            (0,0,18),    
            (1,1,0),
            (1,0,5),
            (2,1,20),
            (2,0,1),
            (3,1,5),
            (3,0,0),
            (4,1,7),
            (4,0,8),          
            (5,1,7),        # From here the palettes are a guess
            (5,0,8),          
            (6,1,7),
            (6,0,7),
            (7,0,8),          
            (7,1,7),
            (8,0,8),          
            (8,1,7),
            (9,0,8),          
            (9,1,7),
            (10,0,8),          
            (10,1,7),
            (11,0,8),          
            (11,1,7),
            (12,0,8),          
            (12,1,7),
            (13,0,8),          
            (13,1,7),
            (14,0,8),          
            (14,1,7) ]

# for lvl in levels:
#     print(f"Showing level {lvl[0]} background palette {lvl[1]} (palette index {lvl[2]})")
#     show_tilemap(level_index=lvl[0], background=lvl[1], palette_index=lvl[2])

#    0   0      18
#    0   1      9
#    1   0      5


# Define the palette as a list of integers
palette_ints = [
    0xD2A85E, 0x9C9FDC, 0xD58B08, 0x179C91,
    0x505197, 0x54995C, 0x00E4A3, 0x945854,
    0xA59B19, 0xA2D400, 0xDED000, 0xD1E5E3,
    0x9CD49B, 0x895031, 0x009854, 0x00890D, 
    0x9EA75E, 0x6F9C11, 0x572515, 0x73CF5F, 
    0xABCBCD, 0xCC739F, 0xA4989E, 0xE4A2A5, 
    0x00F2EE, 0x6F9E90, 0x494400, 0xDAD8A2, 
    0x115221, 0x0C1448, 0x6B63D8, 0x73A5D6, 
    0x66F3B5, 0xEA055D, 0xC66300, 0x00C663, 
    0x14171A, 0x8A00A1, 0x00B5D6, 0x006994, 
    0x5E4262, 0x135265, 0xE700E7, 0xAA03EF, 
    0xE7ACE9, 0x00D600, 0xA5C673, 0xC600A5, 
    0x947394, 0xF75B5B, 0x84116D, 0x951911, 
    0x0063F7, 0x8463C6, 0x000000, 0x000000, 
    0xF6F7F4, 0xE7E7EA, 0x737371, 0xD6D6D4, 
    0xB4B5B4, 0x8D8484, 0x00C6E7, 0xB3A593, 
    0x00D6E7, 0xA89485, 0xD2C6B3, 0xCEC6EF, 
    0x929490, 0xC65252, 0xA7A5A9, 0xF00000, 
    0xD6D6E7, 0xC6B5A5, 0xC6C6C8, 0xF0D6B1, 
    0x00E7E7, 0xE7D6F7, 0x009400, 0xA50052, 
    0x850000, 0x8F846A, 0x632108, 0xE76363, 
    0xF5C684, 0xA53131, 0xEFE7CE, 0x6C6342, 
    0x645200, 0x000093, 0xF79484, 0xD58485, 
    0xD66363, 0xF7F794, 0x424242, 0xC90000, 
    0xCEC68F, 0x00B5E7, 0xD6F784, 0xC8A573, 
    0x646364, 0x000072, 0xEDC6A7, 0xF7F773, 
    0xB5C6B4, 0xD5A5A5, 0xB09472, 0x4AB542, 
    0x006300, 0x84F700, 0xE8D6C6, 0xE7B58D, 
    0xF5E7AE, 0xCEA58E, 0x7394A7, 0x6D4200, 
    0x867300, 0x007309, 0x844200, 0xCAB500, 
    0xD0B592, 0xACA500, 0xE7B572, 0xA90000, 
    0x8E6300, 0xF7A5A7, 0x00948F, 0xA9940A, 
    0x525230, 0xF3F700, 0x698400, 0x946331, 
    0x525252, 0x8F5269, 0xB5E773, 0x429410, 
    0x005200, 0xB500D6, 0x6C84AB, 0x8FB5B1, 
    0xA56321, 0xCE948F, 0x52C6C6, 0x424221, 
    0xAA8400, 0xD6A5CC, 0x949473, 0x73B500, 
    0xACB5EE, 0x8BC6C6, 0x737352, 0x0000CE, 
    0xD2F7EE, 0xF7F7D3, 0x0000E7, 0xF3E700, 
    0x94A5B5, 0xF7D694, 0xA56300, 0xEFA58B, 
    0xB5B5C6, 0x219421, 0x03B500, 0x8473B5, 
    0xD4E7C7, 0x00E700, 0x675225, 0x719472, 
    0x737391, 0xD2A500, 0x84E7C6, 0xB57384, 
    0xD6D691, 0x633100, 0xC6C621, 0xA9B594, 
    0x006352, 0x426300, 0x7373B5, 0x877365, 
    0x314242, 0x52C6A5, 0x63F7D6, 0x731000, 
    0xB46352, 0x003100, 0x84C652, 0xB5B500, 
    0x00C600, 0x421000, 0xAE8484, 0x63A500, 
    0x0052A5, 0x293131, 0xD5C673, 0xC67342, 
    0x0094E7, 0xB5F7E8, 0x0084F7, 0x426373, 
    0xC631B5, 0x429442, 0x94C6B5, 0x8473D6, 
    0x927349, 0x63D600, 0xD684C6, 0x8C94C8, 
    0x0063B5, 0xC8C600, 0xE7B500, 0x00F700, 
    0x0094C6, 0x0042B5, 0xC6846F, 0x85B563, 
    0x879400, 0x686300, 0xD2B5D6, 0x0094AD, 
    0xA5C68B, 0xA52194, 0x212173, 0x528452, 
    0x528400, 0xA78473, 0xAA7300, 0xF3F7B1, 
    0x9494A9, 0xB4C6E7, 0x67524C, 0xF47300, 
    0xF70084, 0xA6D6C7, 0x739484, 0xB5F7B5, 
    0xD67363, 0x00B573, 0xAE6364, 0x7384D6, 
    0x73B594, 0xD36386, 0x8484A7, 0xD37300, 
    0x316300, 0x0000B5, 0x844273, 0x526352, 
    0x211042, 0xB5638F, 0x945200, 0x008494, 
    0x94D6A5, 0xF70031, 0x73C6A5, 0xD65263]

index = 0
output_file = open("tilemap_output.bin", "wb")
text_file = open("tilemap_output.txt", "w")
offset = 0

palette_img = Image.new("P", (1, 1))
# Flatten to bytes
palette_bytes = []
for c in palette_ints:
    r = (c >> 16) & 0xFF
    g = (c >> 8) & 0xFF
    b = c & 0xFF
    palette_bytes += [r, g, b]
palette_img.putpalette(palette_bytes)

road_pals = "2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,180029866,659455,180031487,180029866,180029866,659455,180031487,180029866,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,2040074239,2040074239,2040072601,2040072601,2040074239,2040074239,2040072601,2057964202,2057965567,2057965567,2057964202,2057964202,2057965567,2057965567,2057964202,2077035469,198015949,2077035469,2077035469,2077035469,198015949,2077035469,2077035469,180029866,268372394,180031487,180029866,180029866,268372394,180031487,180029866,2022181000,268372104,2022182911,2022181000,2022181000,268372104,2022182911,2022181000,1968571734,1968577230,1968577230,1968571734,1968571734,1968577230,1968577230,1968571734,2042431933,2025654461,2042431933,2042431933,2042431933,2025654461,2042431933,2042431933,412706970,268396698,412684287,412706970,412706970,268396698,412684287,412706970,176716424,268369930,176689151,176716424,176716424,268369930,176689151,176716424,2057964202,2057965567,2057965567,2057964202,2057964202,2057965567,2057965567,2057964202,2004354936,268371832,268371832,2004354936,2004354936,268371832,268371832,2004354936,2040072601,2040073693,2040073693,2040072601,2040072601,2040073693,2040073693,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601,2040072601,268372377,2040074239,2040072601"
ground_pals ="75986055,75986055,93877656,111769257,129660858,147552459,147552459,147552459,144341146,144341146,162232747,180124348,198015949,215907550,215907550,215907550,92697990,92697990,110589591,128481192,146372793,164264394,164264394,164264394,6844520,6844520,24736121,42627722,60519323,78410924,78410924,78410924,159746,159746,1273875,19165476,37057077,54948678,54948678,54948678,162232747,162232747,162232747,180124348,198015949,215907550,215907550,215907550,3502133,3502133,4616262,5730391,28776,7958649,7958649,7958649,162232747,162232747,180124348,198015949,215907550,233799151,233799151,233799151,56062807,73954408,91846009,109737610,127629211,145520812,145520812,145520812,108557944,108557944,126449545,144341146,144341146,162232747,162232747,162232747,163346876,163346876,181238477,199130078,199130078,217021679,217021679,217021679,109737610,109737610,127629211,145520812,145520812,163412413,163412413,163412413,73995369,73995369,91886970,109778571,109778571,127670172,127670172,127670172,110917276,110917276,128808877,146700478,146700478,164592079,164592079,164592079,78214313,78214313,96105914,113997515,113997515,131889116,131889116,131889116,718744279,718744279,718756567,734550984,750345401,766139818,781934235,1066090379"
sky_pals ="1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,1334857616,2147450879,1342132223,1342115839,804196335,1071517662,1070403533,1069289404,1068175275,1067061146,1065947017,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,260640649,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,382670543,518528744,518528744,518528744,518528744,518528744,518528744,518528744,518528744,518528744,518528744,518524648,518459111,518393574,518328037,518262500,518196963,518131426,518065889,518000352,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,249564896,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,1867542352,2146324462,2145210333,2144096204,2142982075,2141867946,2140753817,2139639688,2138525559,2137411430,2136297301,2135183172,2134069043,2132954914,2131840785,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,1325420288,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,259264372,501161439,501161439,501161439,501157343,501091806,501026269,500960732,500895195,500829658,500764121,500698584,500633047,500567510,500501973,500436436,500370899,500305362,500239825,500174288,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,231738832,1190086383,668944351,684673231,700402111,180308671,197086143,482233534,1051545261,1067192220,1066078091,1064963962,1063849833,794365785,1061687112,1060572983,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,181209805,836,1933837397,1951728998,1969620599,1987512200,2005403801,2023295402,2041187003,2059078604,2076970205,2094861806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,2112781806,1190086383,668944351,684673231,700402111,180308671,197086143,482233534,1051545261,1067192220,1066078091,1064963962,1063849833,794365785,1061687112,1060572983,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,255266615,167725311,150947839,134170367,117401071,99575007,81748943,63922879,46096815,28262831,28263087,61817775,78595247,95372719,112150191,128927663,145705135,162482607,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,179243695,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,1332039525,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401,2141290401"

def output_stripe_palette(name: str, road_pals: str):
    pals = road_pals.split(",")
    text_file.write(f"# Palette data for {name}\n")
    text_file.write(f"const {name} = const Array<Int> [\n")
    for i, p in enumerate(pals):
        converted = convert_palette(int(p))
        x = converted[0]<<16 | converted[1]<<8 | converted[2]
        text_file.write(f"    0x{x:X},\n")
    text_file.write("]\n\n")

output_stripe_palette("roadPalettes", road_pals)
output_stripe_palette("groundPalettes", ground_pals)
output_stripe_palette("skyPalettes", sky_pals)


def output_sprite(img:Image, sprite_name: str):
    global index
    global offset
    global output_file
    size_x = img.size[0]
    size_y = img.size[1]
    text_file.write(f"backgroundImage[{index}] = createImage(\"{sprite_name}\", {size_x}, {size_y},spriteData, {offset})\n")
    index += 1
    offset += size_x * size_y

    # Get the transparent pixels mask
    print(img.mode)
    if img.mode == "RGBA":
        rgba = np.array(img)
        mask = rgba[..., 3] <= 128
    elif img.mode == "P":
        pixels = np.array(img)
        mask = (pixels == 0)
    else:
        mask = np.zeros(img.size[::-1], dtype=bool)

    # Quantize the image
    quantized = img.convert("RGB").quantize("P", palette=palette_img)

    # Set back transparent pixels to palette index 0
    data = np.array(quantized)
    data[mask] = 0
    quantized = Image.fromarray(data.astype("uint8"))

    pixels = quantized.tobytes()
    output_file.write(pixels)

for lvl in levels:
    print(f"Processing level {lvl[0]} background palette {lvl[1]} (palette index {lvl[2]})")
    img = get_tilemap_image(level_index=lvl[0], background=lvl[1], palette_index=lvl[2])
    output_sprite(img, f"tilemap_level{lvl[0]}_{lvl[1]}")