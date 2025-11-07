import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List
import struct

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
class Level:
    name: str
    type: int
    path: List[PathPoint] = field(default_factory=list)
    scenery: List[SceneryPoint] = field(default_factory=list)
    width: List[WidthPoint] = field(default_factory=list)




def load_game_data(filename: str):
    tree = ET.parse(filename)
    root = tree.getroot()

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

        levels.append(lvl)

    return levels, patterns

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
        for i in range(length):
            if i%7==0:
                freq = pattern.freq
                sprite_index = 0
            # print(f"  pos {sc.pos+i} freq {freq:04x}")
            if freq&0x8000:
                sprite = pattern.sprites[sprite_index]
                print(f"Adding sprite {sprite.name} at {sprite.x},{sprite.y},{sc.pos+i}")
                flat_sprites.append( FlatSprite(
                    pos=sc.pos + i,
                    x=sprite.x,
                    y=sprite.y,
                    type=sprite.type,
                    pal=sprite.pal,
                    props=sprite.props
                ))
                sprite_index = (sprite_index + 1) % len(pattern.sprites)
            if freq&0x4000:
                sprite = pattern.sprites[sprite_index]
                print(f"Adding sprite {sprite.name} at {sprite.x},{sprite.y},{sc.pos+i}")
                flat_sprites.append(FlatSprite(
                    pos=sc.pos + i,
                    x=sprite.x,
                    y=sprite.y,
                    type=sprite.type,
                    pal=sprite.pal,
                    props=sprite.props
                ))
                sprite_index = (sprite_index + 1) % len(pattern.sprites)
            freq <<= 2
    return flat_sprites

def exportPath(level: Level, sprites: List[FlatSprite]):
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
        f.write(struct.pack('<fff', 0.0, 12.0, 0))  # initial width
        for wd in level.width:
            newWidth = 12.0 + wd.width/20.0    # Outrun stores width as road offset. Convert to full width
            f.write(struct.pack('<fff', wd.pos, newWidth, wd.change))

        # Output the scenery sprites
        f.write(len(sprites).to_bytes(4, byteorder='little'))
        for sp in sprites:
            f.write(struct.pack('<fffIII', sp.pos+40, sp.x, sp.y, sp.type, sp.pal, sp.props))
        print("Exported", len(sprites), "sprites")
        
        


levels, patterns = load_game_data('../../../Downloads/LayOut-win32/outrun_data.xml')

lvl = levels[1]
sprites = expand_scenery(lvl, patterns)
exportPath(lvl, sprites)

# print(f"{lvl.name} has {len(sprites)} expanded sprites")
# for s in sprites[:100]:
#     print(s)

