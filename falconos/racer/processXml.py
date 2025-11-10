import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List
import struct
import numpy as np
from PIL import Image

@dataclass
class Sprite:
    name: str
    type: int
    x: int
    y: int
    pal: int
    props: int

@dataclass
class FlatSprite:
    pos: int
    x: int
    y: int
    type: int
    pal: int
    props: int

@dataclass
class Pattern:
    name: str
    freq: int
    sprites: List[Sprite] = field(default_factory=list)

@dataclass
class SceneryPoint:
    pos: int
    length: int
    index: int  # pattern index

@dataclass
class PathPoint:
    index: int
    length: int
    angle: float

@dataclass
class WidthPoint:
    pos: int
    width: float
    change: float

@dataclass
class HeightMap:
    index: int
    step: int
    values: List[int]

@dataclass
class HeightPoint:
    pos: int
    map: int

@dataclass
class Level:
    name: str
    type: int
    path: List[PathPoint] = field(default_factory=list)
    scenery: List[SceneryPoint] = field(default_factory=list)
    width: List[WidthPoint] = field(default_factory=list)
    heightPoints: List[HeightPoint] = field(default_factory=list)

# Map sprite names to file names
sprite_file_map = {
    "1: Tree - European":               "Sprite_0395_0.png",
    "2: Road Debris":                   "Sprite_1184_226.png",
    "3: Strip - Sand":                  "Sprite_0495_181.png",
    "4: Rock - Sculpted":               "Sprite_0691_50.png",
    "6: Sign - Directional":            "Sprite_0666_33.png",
    "7: Sign - Diving School":          "Sprite_0671_52.png",
    "9: Pole - Thin":                   "Sprite_0956_51.png",
    "10: Sign - Diving School (Dupe)":  "Sprite_0671_52.png",
    "11: Sign - Check/Goal Banner":     "Sprite_0804_42.png",
    "14: Sign - Start Adverts RHS":     "Sprite_0809_123.png",
    "15: Decor - Ancient":              "Sprite_0884_160.png",
    "16: Tree - Palm":                  "Sprite_0420_54.png",
    "17: Sign - Check/Goal Pole":       "Sprite_0814_221.png",
    "18: Tree - Dead medium":           "Sprite_0480_178.png",
    "19: Pole - Medium":                "Sprite_0961_25.png",
    "21: Decor - Ancient Dupe":         "Sprite_0884_160.png",
    "22: Finish - People Strip":        "Sprite_1373_206.png",
    "23: Pole - Thick":                 "Sprite_0971_25.png",
    "25: Bush 1":                       "Sprite_0405_75.png",
    "26: Goal Banner Post Large":       "Sprite_1193_230.png",
    "27: Goal Banner Post Small":       "Sprite_0976_230.png",
    "28: Windsurfer":                   "Sprite_0981_66.png",
    "30: Tree - Pine":                  "Sprite_0400_75.png",
    "31: Bush 2":                       "Sprite_0410_76.png",
    "32: Bush Double":                  "Sprite_0415_76.png",
    "33: Tree - Oak":                   "Sprite_0425_77.png",
    "34: Building - Flaminco":          "Sprite_0819_78.png",
    "35: Tree - No Leaves":             "Sprite_0435_79.png",
    "36: Sign - Danke":                 "Sprite_0641_80.png",
    "37: Building - Hut":               "Sprite_0824_196.png",
    "38: Bush 3":                       "Sprite_0450_43.png",
    "39: Decor - Oil":                  "Sprite_0646_83.png",
    "40: Decor - Oil Pump 1":           "Sprite_0986_128.png",
    "41: Decor - Oil Pump 2":           "Sprite_0829_128.png",
    "42: Building - Windmill":          "Sprite_0839_87.png",
    "43: Porche":                       "Sprite_0034_251.png",
    "44: Porche - dupe":                "Sprite_0034_251.png",
    "45: Porche - dupe":                "Sprite_0034_251.png",
    "46: Sign - Motorcross":            "Sprite_0651_91.png",
    "47: Sign - Directions":            "Sprite_0656_92.png",
    "48: Arch - Top Section":           "Sprite_0597_95.png",
    "49: Arch - Pillar":                "Sprite_0592_97.png",
    "53: Decor - Crowd Stand":          "Sprite_0834_105.png",
    "57: Decor - barrel":               "Sprite_1000_109.png",
    "58: Porche - dupe":                "Sprite_0039_251.png",
    "59: Porche - dupe":                "Sprite_0034_251.png",
    "60: Strip - Crops":                "Sprite_0602_71.png",
    "63: Strip - Water":                "Sprite_0576_6.png",
    "66 Strip - Dead Twigs":            "Sprite_0632_228.png",
    "71: Sign - Ice Cream Parlor":      "Sprite_0661_27.png",
    "78: Rock - Horizontal 1":          "Sprite_0859_160.png",
    "85: Rock - Vertical":              "Sprite_0864_160.png",
    "86: Rock - Medium":                "Sprite_0869_160.png",
    "87: Rock - Tiny":                  "Sprite_0874_160.png",
    "88: Rock - Horizontal 2":          "Sprite_0879_160.png",
    "89: Bush 4 - Tropical":            "Sprite_0445_43.png",
    "90: Building - Tower":             "Sprite_0844_44.png",
    "91: Building - Tower Top":         "Sprite_0849_44.png",
    "92: Building - Tower Roof":        "Sprite_0854_44.png",
    "93: Bush 5":                       "Sprite_0430_77.png",
    "94: Sign - SEGA 1":                "Sprite_0676_164.png",
    "95: Bush 6":                       "Sprite_0440_79.png",
    "96: Catctus top":                  "Sprite_0465_133.png",
    "97: Sign - SEGA 2":                "Sprite_0686_166.png",
    "98: Sign - OutRun Deluxe":         "Sprite_0681_165.png",
    "109: Rock - Halved 1":             "Sprite_0696_50.png",
    "110: Rock - Halved 2":             "Sprite_0701_50.png",
    "111: Cactus 1":                    "Sprite_0455_133.png",
    "112: Cactus 2":                    "Sprite_0460_133.png",
    "113: Start Left":                  "Sprite_0706_69.png",
    "114: Start Right":                 "Sprite_0711_69.png",
    "116: Strip - Clouds":              "Sprite_1364_205.png",
    "119: Strip - Pebbles":             "Sprite_1378_227.png",
    "120: Advert Left":                 "Sprite_0791_123.png",
    "130: Tree - Twiggy":               "Sprite_0470_178.png",
    "131: Tree - Stump":                "Sprite_0475_178.png",
    "132: Tree - Dead Half":            "Sprite_0480_178.png",
    "133: Bush 7":                      "Sprite_0490_179.png",
    "134: Bush 8":                      "Sprite_0571_0.png"
}

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

# Flatten to bytes
palette_bytes = []
for c in palette_ints:
    r = (c >> 16) & 0xFF
    g = (c >> 8) & 0xFF
    b = c & 0xFF
    palette_bytes += [r, g, b]

palette_img = Image.new("P", (1, 1))
palette_img.putpalette(palette_bytes)

output_file = open("sprites.bin", "wb")
text_file = open("sprites.txt", "w")
offset = 0
index = 0

with open("C:/Users/simon/Downloads/outrun_amiga_edition_v092/mikey/outrun16.pal", "rb") as palette_file:
    palette_data = palette_file.read()
    palette_bin = list(struct.unpack(f"{len(palette_data)}B", palette_data))


def convert_sprite(sprite_name, file_name):
    path = "../../../Downloads/outrun_amiga_edition_v092/mikey/sprites256bit/"+file_name
    print(f"Converting sprite {sprite_name} from file {path}")

    img = Image.open(path)
    output_sprite(img, sprite_name)

def apply_palette(img: Image.Image, paletteIndex: int) -> Image.Image:
    colors_per_palette = 16
    bytes_per_color = 3

    # Compute start offset
    start = paletteIndex * colors_per_palette * bytes_per_color
    end = start + colors_per_palette * bytes_per_color
    rgb_palette = palette_bin[start:end]

    # Pad to full 256 colors
    rgb_palette.extend([0, 0, 0] * (256 - colors_per_palette))

    paletted_img = img.copy()
    paletted_img.putpalette(rgb_palette)
    return paletted_img

def convert_sprite_with_palette(sprite_name, file_name, paletteIndex):
    path = "../../../Downloads/outrun_amiga_edition_v092/mikey/sprites16col/"+file_name
    print(f"Converting sprite {sprite_name} from file {path} with palette {paletteIndex}")

    img = Image.open(path)
    # img.show()
    paletted_img = apply_palette(img, paletteIndex)
    output_sprite(paletted_img, sprite_name)

def output_sprite(img:Image, sprite_name: str):
    global index
    global offset
    global output_file
    size_x = img.size[0]
    size_y = img.size[1]
    text_file.write(f"spriteArray[{index}] = createImage(\"{sprite_name}\", {size_x}, {size_y}, {offset})\n")
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


# Keep track of unique sprite/pallette combinations
unique_sprite_variants = {}
sprite_variant_list = []

def load_game_data(filename: str):
    tree = ET.parse(filename)
    root = tree.getroot()

    # Parse shared height maps
    height_maps = {}
    for hm in root.findall('.//heightMaps/entry'):
        idx = int(hm.get('name').split()[-1])  # extract the number after "Segment"
        step = int(hm.get('step', '1'))
        data_el = hm.find('data')
        data_str = data_el.get('value', '') if data_el is not None else ''
        values = [int(v) for v in data_str.replace(',', ' ').split() if v]
        print(f"Loaded height map {idx} with step {step} and {len(values)} values '{data_str}'")
        height_maps[idx] = HeightMap(index=idx, step=step, values=values)

    # Parse scenery patterns
    patterns = {}
    for pat_el in root.findall('.//sceneryPatterns/pattern'):
        name = pat_el.get('name')
        freq = int(pat_el.get('freq', '0'))
        sprites = []
        for s_el in pat_el.findall('sprite'):
            sprites.append(Sprite(
                name=s_el.get('name', ''),
                type=int(s_el.get('type', '0')),
                x=int(s_el.get('x', '0')),
                y=int(s_el.get('y', '0')),
                pal=int(s_el.get('pal', '0')),
                props=int(s_el.get('props', '0')),
            ))
        # Patterns are often numbered at start of name: "1: Trees - European"
        index = int(name.split(':')[0])
        patterns[index] = Pattern(name, freq, sprites)

    # Parse levels
    levels = []
    for lvl_el in root.findall('.//level'):
        lvl = Level(
            name=lvl_el.get('name'),
            type=int(lvl_el.get('type', '0')),
        )

        for pt in lvl_el.findall('./pathData/point'):
            lvl.path.append(PathPoint(
                index=int(pt.get('index')),
                length=int(pt.get('length')),
                angle=float(pt.get('angle', '0'))
            ))

        for sc in lvl_el.findall('./sceneryData/point'):
            lvl.scenery.append(SceneryPoint(
                pos=int(sc.get('pos')),
                length=int(sc.get('length')),
                index=int(sc.get('index'))
            ))
        for wd in lvl_el.findall('./widthData/point'):
            lvl.width.append(WidthPoint(
                pos=int(wd.get('pos')),
                width=float(wd.get('width')),
                change=float(wd.get('change'))
            ))
        for hd in lvl_el.findall('./heightData/point'):
            lvl.heightPoints.append(HeightPoint(
                pos=int(hd.get('pos')),
                map=int(hd.get('map'))
            ))

        levels.append(lvl)

    return levels, patterns, height_maps

def build_flat_slope_map(level: Level, height_maps: dict[int, HeightMap]) -> list[float]:
    # Determine full track length (from path data)
    max_pos = sum(pt.length for pt in level.path)
    slope_map = [0.0] * max_pos

    # Overlay each height segment
    print(f"Building flat slope map for level {level.name} with length {max_pos} num points {len(level.heightPoints)}")
    print(height_maps)
    for hp in level.heightPoints:
        hmap = height_maps.get(hp.map)
        if not hmap or not hmap.values:
            print(f"  Skipping height map {hp.map} at pos {hp.pos} - not found or empty")
            continue

        step = hmap.step
        vals = hmap.values
        print(f"Processing height map {hp.map} at pos {hp.pos} with step {step} and {len(vals)} values")

        # Precompute normalized slopes (convert raw delta values to slopes per step)
        slopes = [v / step / 128.0 for v in vals]

        # For each pair of consecutive slope control points, interpolate linearly
        for i in range(len(slopes) - 1):
            s0 = slopes[i]
            s1 = slopes[i + 1]
            for j in range(step):
                # Interpolation fraction within this step
                t = j / step
                slope = s0 + (s1 - s0) * t
                pos = hp.pos + i * step + j
                if pos < len(slope_map):
                    slope_map[pos] += slope
    return slope_map


def expand_scenery(level: Level, patterns: dict[int, Pattern]):
    flat_sprites = []

    for sc in level.scenery:
        pattern = patterns.get(sc.index+1)
        if not pattern:
            continue
        # length is min of scenery length and distance to next scenery point
        next_sc_index = level.scenery.index(sc) + 1
        length =sc.length
        if next_sc_index < len(level.scenery) and level.scenery[next_sc_index].pos > sc.pos:
            length = min(length, level.scenery[next_sc_index].pos - sc.pos)

        print(f"Expanding scenery pattern {pattern.name} at pos {sc.pos} length {length}  freq {pattern.freq}")
        # Pattern consists of multiple sprites, placed at patterns spaced over 8 units of length
        # pattern.freq is a bitmask indicating which of the 8 positions to place sprites at - max 2 sprites per location
        sprite_index = 0
        for i in range(length):
            if i%7==0:
                freq = pattern.freq
            # print(f"  pos {sc.pos+i} freq {freq:04x}")
            if freq&0x8000:
                sprite = pattern.sprites[sprite_index]

                key = (sprite.type, sprite.pal)
                if key not in unique_sprite_variants:
                    unique_id = len(unique_sprite_variants)
                    unique_sprite_variants[key] = unique_id
                    sprite_variant_list.append({
                        'base_type': sprite.type,
                        'pal': sprite.pal,
                        'unique_id': unique_id,
                        'name': sprite.name
                    })

                unique_id = unique_sprite_variants[key]

                print(f"Adding sprite {unique_id} '{sprite.name}' at {sprite.x},{sprite.y},{sc.pos+i}")
                flat_sprites.append( FlatSprite(
                    pos=sc.pos + i,
                    x=sprite.x,
                    y=sprite.y,
                    type=unique_id,
                    pal=sprite.pal,
                    props=sprite.props
                ))
                sprite_index = (sprite_index + 1) % len(pattern.sprites)

            if freq&0x4000:
                sprite = pattern.sprites[sprite_index]
                key = (sprite.type, sprite.pal)
                
                if key not in unique_sprite_variants:
                    unique_id = len(unique_sprite_variants)
                    unique_sprite_variants[key] = unique_id
                    sprite_variant_list.append({
                        'base_type': sprite.type,
                        'pal': sprite.pal,
                        'unique_id': unique_id,
                        'name': sprite.name
                    })

                unique_id = unique_sprite_variants[key]
                print(f"Adding sprite {sprite.name} at {sprite.x},{sprite.y},{sc.pos+i}")
                flat_sprites.append(FlatSprite(
                    pos=sc.pos + i,
                    x=sprite.x,
                    y=sprite.y,
                    type=unique_id,
                    pal=sprite.pal,
                    props=sprite.props
                ))
                sprite_index = (sprite_index + 1) % len(pattern.sprites)
            freq <<= 2
    return flat_sprites


def assortedHacks(sprites: List[FlatSprite]):
    for sp in sprites:
        # The waterfront on the beach is too far left - out of screen
        if sp.type==62 and sp.x<-100:
            sp.x += 75 
        if sp.type==27 and sp.x<-50:
            sp.x += 40

def exportPath(level: Level, sprites: List[FlatSprite], flat_slope_map: List[float]):
    startPos = 0.0
    with open("level_path.bin", "wb") as f:
        # Output the curvature data
        f.write(len(level.path).to_bytes(4, byteorder='little'))
        for seg in level.path:
            length = float(seg.length)
            endPos = startPos + length
            curvature = seg.angle / -150.0    # convert degrees to radians per unit length
            f.write(struct.pack('<ffff', startPos, endPos, curvature, 0.0))
            startPos = endPos

        # Output the width data
        f.write((len(level.width)+1).to_bytes(4, byteorder='little'))
        f.write(struct.pack('<fff', 0.0, 6.0, 0))  # initial width
        for wd in level.width:
            newWidth = 3 + wd.width / 72    # convert from game units to number of lanes
            if newWidth>7.0:                # When the road divides add an extra space for the divider
                newWidth = 8.0
            # print(f"Width point at pos {wd.pos} width {newWidth} change {wd.change}")
            f.write(struct.pack('<fff', wd.pos, newWidth, wd.change))

        # Output the scenery sprites
        f.write(len(sprites).to_bytes(4, byteorder='little'))
        for sp in sprites:
            f.write(struct.pack('<fffIII', sp.pos+40, sp.x, sp.y, sp.type, sp.pal, sp.props))
            print(f"Exporting sprite at pos {sp.pos+40} x {sp.x} y {sp.y} type {sp.type} pal {sp.pal} props {sp.props}")
        print("Exported", len(sprites), "sprites")

        # Output flat slope map
        f.write(len(flat_slope_map).to_bytes(4, byteorder='little'))
        for slope in flat_slope_map:
            f.write(struct.pack('<f', slope))
        
def output_non_scenery_sprites():
    convert_sprite("car_straight", "Sprite_0001_2.png")
    convert_sprite("car_down", "Sprite_0002_2.png")
    convert_sprite("car_up", "Sprite_0003_2.png")
    convert_sprite("car_turn", "Sprite_0004_2.png")
    convert_sprite("car_downturn", "Sprite_0005_2.png")
    convert_sprite("car_upturn", "Sprite_0006_2.png")
    convert_sprite("car_turn2", "Sprite_0007_2.png")
    convert_sprite("smoke1", "Sprite_1189_226.png")
    convert_sprite("smoke2", "Sprite_1190_226.png")
    convert_sprite("smoke3", "Sprite_1191_226.png")
    convert_sprite("smoke4", "Sprite_1192_226.png")
    convert_sprite("car_spin1", "Sprite_0099_2.png")
    convert_sprite("car_spin2", "Sprite_0100_2.png")
    convert_sprite("car_spin3", "Sprite_0101_2.png")
    convert_sprite("car_spin4", "Sprite_0102_2.png")
    convert_sprite("car_flip1", "Sprite_1142_111.png")
    convert_sprite("car_flip2", "Sprite_1143_111.png")
    convert_sprite("car_flip3", "Sprite_1144_111.png")
    convert_sprite("car_flip4", "Sprite_1145_111.png")
    convert_sprite("car_flip5", "Sprite_1146_111.png")
    convert_sprite("car_flip6", "Sprite_1147_111.png")
    convert_sprite("car_flip7", "Sprite_1148_111.png")

    convert_sprite("npc_car_1",  "Sprite_0139_15.png")
    convert_sprite("npc_car_2",  "Sprite_0140_17.png")
    convert_sprite("npc_car_3",  "Sprite_0255_202.png")
    convert_sprite("npc_car_4",  "Sprite_0256_200.png")
    convert_sprite("npc_car_5",  "Sprite_0305_207.png")
    convert_sprite("npc_car_6",  "Sprite_0306_130.png")
    convert_sprite("npc_car_7",  "Sprite_0307_60.png")
    convert_sprite("npc_car_8",  "Sprite_0308_63.png")
    convert_sprite("npc_car_9",  "Sprite_0265_207.png")
    convert_sprite("npc_car_10", "Sprite_0266_130.png")
    convert_sprite("npc_car_11", "Sprite_0308_63.png")
    convert_sprite("npc_car_12", "Sprite_0308_63.png")
    convert_sprite("npc_car_13", "Sprite_0104_253.png")
    convert_sprite("npc_car_14", "Sprite_0103_58.png")
    convert_sprite("npc_car_15", "Sprite_0012_118.png")
    convert_sprite("npc_car_16", "Sprite_0013_120.png")
    convert_sprite("npc_car_17", "Sprite_0011_251.png")
    convert_sprite("npc_car_18", "Sprite_0010_249.png")
    convert_sprite("npc_car_19", "Sprite_0009_114.png")
        


levels, patterns, HeightMaps = load_game_data('../../../Downloads/LayOut-win32/outrun_data.xml')

lvl = levels[0]
sprites = expand_scenery(lvl, patterns)
flat_slope_map = build_flat_slope_map(lvl, HeightMaps)
assortedHacks(sprites)
exportPath(lvl, sprites, flat_slope_map)

output_non_scenery_sprites()
print("Unique sprite variants:")
for sv in sprite_variant_list:
    # get file name
    file_name = sprite_file_map.get(sv['name'], 'UNKNOWN.png')
    if file_name=='UNKNOWN.png':
        print(f"Warning: No file mapping for sprite name '{sv['name']}'")
    convert_sprite_with_palette(sv['name'], file_name, sv['pal'])



# print(f"{lvl.name} has {len(sprites)} expanded sprites")
# for s in sprites[:100]:
#     print(s)


text_file.close()
output_file.close()