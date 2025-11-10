import numpy as np
import struct
from PIL import Image


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

def apply_palette(img: Image.Image, paletteIndex: int) -> Image.Image:
    # Each color is 4 bytes (RGBA)
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

def convert_sprite(sprite_name, file_name):
    global offset
    global output_file
    global text_file
    global index
    path = "../../../Downloads/outrun_amiga_edition_v092/mikey/sprites256bit/"+file_name

    img = Image.open(path)

    size_x = img.size[0]
    size_y = img.size[1]
    text_file.write(f"spriteArray[{index}] = createImage(\"{sprite_name}\", {size_x}, {size_y}, {offset})\n")
    index += 1
    offset += size_x * size_y

    # Get the transparent pixels mask
    rgba = np.array(img)
    mask = rgba[..., 3] <= 128

    # Quantize the image
    quantized = img.convert("RGB").quantize("P", palette=palette_img)

    # Set back transparent pixels to palette index 0
    data = np.array(quantized)
    data[mask] = 0
    quantized = Image.fromarray(data.astype("uint8"))

    pixels = quantized.tobytes()
    output_file.write(pixels)


dummy = "Sprite_0053_251.png"
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


convert_sprite("1: Tree - European",           "Sprite_0395_0.png")
convert_sprite("2: Road Debris",               "Sprite_1184_226.png")
convert_sprite("3: Strip - Sand",              "Sprite_0495_181.png")
convert_sprite("4: Rock - Sculpted",           "Sprite_0691_50.png")
convert_sprite("5: Amin - Bike1",              dummy)
convert_sprite("6: Sign - Directional",        "Sprite_0666_33.png")
convert_sprite("7: Sign - Diving School",      "Sprite_0671_52.png")
convert_sprite("8: Anim - Bike 2",             dummy)
convert_sprite("9: Pole - Thin",               "Sprite_0956_51.png")
convert_sprite("10: Sign - Diving School",     "Sprite_0671_52.png")
convert_sprite("11: Sign - Check/Goal Banner", "Sprite_0804_42.png")
convert_sprite("12: Anim - Flagman 1",         dummy)
convert_sprite("13: Anim - Flagman 2",         dummy)
convert_sprite("14: Sign - Start Adverts RHS", "Sprite_0809_123.png")
convert_sprite("15: Decor - Ancient",          "Sprite_0884_160.png")
convert_sprite("16: Tree - Palm",              "Sprite_0420_54.png")
convert_sprite("17: Sign - Check/Goal Pole",   "Sprite_0814_221.png")
convert_sprite("18: Tree - Dead medium",       "Sprite_0480_178.png")
convert_sprite("19: Pole - Medium",             "Sprite_0961_25.png")
convert_sprite("20: Anim - Bike 3"             , dummy)
convert_sprite("21: Decor - Ancient Dupe",    "Sprite_0884_160.png")
convert_sprite("22: Finish - People Strip",   "Sprite_1373_206.png")
convert_sprite("23: Pole - Thick",            "Sprite_0971_25.png")
convert_sprite("24: Anim - Flagman 3 Back Turned", dummy)
convert_sprite("25: Bush 1",                  "Sprite_0405_75.png")
convert_sprite("26: Goal Banner Post Large",  "Sprite_1193_230.png")
convert_sprite("27: Goal Banner Post Small",  "Sprite_0976_230.png")
convert_sprite("28: Windsurfer",              "Sprite_0981_66.png")
convert_sprite("29: Anim - Arabian Man",      dummy)
convert_sprite("30: Tree - Pine",             "Sprite_0400_75.png")
convert_sprite("31: Bush 2",                  "Sprite_0410_76.png")
convert_sprite("32: Bush Double",             "Sprite_0415_76.png")
convert_sprite("33: Tree - Oak",              "Sprite_0425_77.png")
convert_sprite("34: Building - Flaminco",   "Sprite_0819_78.png")
convert_sprite("35: Tree - No Leaves",      "Sprite_0435_79.png")
convert_sprite("36: Sign - Danke",          "Sprite_0641_80.png")
convert_sprite("37: Building - Hut",        "Sprite_0824_196.png")
convert_sprite("38: Bush 3",                "Sprite_0450_43.png")
convert_sprite("39: Decor - Oil",           "Sprite_0646_83.png")
convert_sprite("40: Decor - Oil Pump 1",    "Sprite_0986_128.png")
convert_sprite("41: Decor - Oil Pump 2",    "Sprite_0829_128.png")
convert_sprite("42: Building - Windmill",   "Sprite_0839_87.png")
convert_sprite("43: Porche",                "Sprite_0034_251.png")
convert_sprite("44: Porche - dupe",         "Sprite_0034_251.png")
convert_sprite("45: Porche - dupe",         "Sprite_0034_251.png")
convert_sprite("46: Sign - Motorcross",     "Sprite_0651_91.png")
convert_sprite("47: Sign - Directions",     "Sprite_0656_92.png")
convert_sprite("48: Arch - Top Section",    "Sprite_0597_95.png")
convert_sprite("49: Arch - Pillar",         "Sprite_0592_97.png")
convert_sprite("50: Vehicle",              dummy)
convert_sprite("51: Vehicle",              dummy)
convert_sprite("52: Vehicle",              dummy)
convert_sprite("53: Decor - Crowd Stand",   "Sprite_0834_105.png")
convert_sprite("54: Vehicle",              dummy)
convert_sprite("55: Vehicle",              dummy)
convert_sprite("56: Vehicle",              dummy)
convert_sprite("57: Decor - barrel",       "Sprite_1000_109.png")
convert_sprite("58: Porche - dupe",        "Sprite_0039_251.png")
convert_sprite("59: Porche - dupe",        "Sprite_0034_251.png")
convert_sprite("60: Strip - Crops",        "Sprite_0602_71.png")
convert_sprite("61: Vehicle",              dummy)
convert_sprite("62: Vehicle",              dummy)
convert_sprite("63: Strip - Water",         "Sprite_0576_6.png")
convert_sprite("64: Vehicle",              dummy)
convert_sprite("65: Vehicle",              dummy)
convert_sprite("66 Strip - Dead Twigs",    "Sprite_0632_228.png")
convert_sprite("67: Vehicle",              dummy)
convert_sprite("68: Vehicle",              dummy)
convert_sprite("69: Vehicle",              dummy)
convert_sprite("70: Shadow",               dummy)
convert_sprite("71: Sign - Ice Cream Parlor","Sprite_0661_27.png")
convert_sprite("72: Vehicle",              dummy)
convert_sprite("73: Vehicle",              dummy)
convert_sprite("74: Vehicle",              dummy)
convert_sprite("75: Vehicle",              dummy)
convert_sprite("76: Vehicle",              dummy)
convert_sprite("77: Vehicle",              dummy)
convert_sprite("78: Rock - Horizontal 1",     "Sprite_0859_160.png")
convert_sprite("79: Vehicle",              dummy)
convert_sprite("80: Vehicle",              dummy)
convert_sprite("81: Vehicle",              dummy)
convert_sprite("82: Vehicle",              dummy)
convert_sprite("83: Vehicle",              dummy)
convert_sprite("84: Vehicle",              dummy)
convert_sprite("85: Rock - Vertical",         "Sprite_0864_160.png")
convert_sprite("86: Rock - Medium",           "Sprite_0869_160.png")
convert_sprite("87: Rock - Tiny",             "Sprite_0874_160.png")
convert_sprite("88: Rock - Horizontal 2",     "Sprite_0879_160.png")
convert_sprite("89: Bush 4 - Tropical" ,      "Sprite_0445_43.png")
convert_sprite("90: Building - Tower",        "Sprite_0844_44.png")
convert_sprite("91: Building - Tower Top",    "Sprite_0849_44.png")
convert_sprite("92: Building - Tower Roof",   "Sprite_0854_44.png")
convert_sprite("93: Bush 5",                  "Sprite_0430_77.png")
convert_sprite("94: Sign - SEGA 1",           "Sprite_0676_164.png")
convert_sprite("95: Bush 6",                  "Sprite_0440_79.png")
convert_sprite("96: Catctus top",             "Sprite_0465_133.png")
convert_sprite("97: Sign - SEGA 2",           "Sprite_0686_166.png")
convert_sprite("98: Sign - OutRun Deluxe",    "Sprite_0681_165.png")
convert_sprite("99: Anim - FlagMan",              dummy)
convert_sprite("100: Anim - FlagMan",             dummy)
convert_sprite("101: Anim - FlagMan",             dummy)
convert_sprite("102: Anim - FlagMan",             dummy)
convert_sprite("103: Anim - FlagMan",             dummy)
convert_sprite("104: Anim - FlagMan",             dummy)
convert_sprite("105: Anim - FlagMan",             dummy)
convert_sprite("106: Anim - FlagMan",             dummy)
convert_sprite("107: Anim - FlagMan",             dummy)
convert_sprite("108: Anim - FlagMan",             dummy)
convert_sprite("109: Rock - Halved 1",        "Sprite_0696_50.png")
convert_sprite("110: Rock - Halved 2",        "Sprite_0701_50.png")
convert_sprite("111: Cactus 1",               "Sprite_0455_133.png")
convert_sprite("112: Cactus 2",               "Sprite_0460_133.png")
convert_sprite("113: Start Left",             "Sprite_0706_69.png")
convert_sprite("114: Start Right",            "Sprite_0711_69.png")
convert_sprite("115: anim",                   dummy)
convert_sprite("116: Strip - Clouds",         "Sprite_1364_205.png")
convert_sprite("117: anim",                   dummy)
convert_sprite("118: anim",                   dummy)
convert_sprite("119: Strip - Pebbles",        "Sprite_1378_227.png")
convert_sprite("120: Advert Left",            "Sprite_0791_123.png")
convert_sprite("121: anim",                   dummy)
convert_sprite("122: anim",                   dummy)
convert_sprite("123: anim",                   dummy)
convert_sprite("124: anim",                   dummy)
convert_sprite("125: anim",                   dummy)
convert_sprite("126: anim",                   dummy)
convert_sprite("127: anim",                   dummy)
convert_sprite("128: anim",                   dummy)
convert_sprite("129: anim",                   dummy)
convert_sprite("130: Tree - Twiggy",          "Sprite_0470_178.png")
convert_sprite("131: Tree - Stump",           "Sprite_0475_178.png")
convert_sprite("132: Tree - Dead Half",       "Sprite_0480_178.png")
convert_sprite("133: Bush 7",                 "Sprite_0490_179.png")
convert_sprite("134: Bush 8",                 "Sprite_0571_0.png")
# convert_sprite("135: Goal Banner Left")
# convert_sprite("136: Goal Banner Right")
# convert_sprite("137: Goal Banner Dupe")
# convert_sprite("146: Anim - Camel 1")
# convert_sprite("148: Anim - Camel 2")
# convert_sprite("149: Anim - Camel 3")
# convert_sprite("150: Anim - Camel 4")