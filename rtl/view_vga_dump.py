import numpy as np
from PIL import Image
import sys

def load_vga_dump(filename):
    pixels = []

    max_x = 0
    max_y = 0

    with open(filename) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue

            x, y, r, g, b = map(int, line.split())
            pixels.append((x, y, r, g, b))

            if x > max_x: max_x = x
            if y > max_y: max_y = y

    width  = max_x + 1
    height = max_y + 1

    print(f"Detected resolution: {width} x {height}")

    img = np.zeros((height, width, 3), dtype=np.uint8)

    for x, y, r, g, b in pixels:
        img[y, x, 0] = r
        img[y, x, 1] = g
        img[y, x, 2] = b

    return img

def main():
    if len(sys.argv) < 2:
        print("Usage: python view_vga_dump.py vga_dump.txt [out.png]")
        sys.exit(1)

    infile = sys.argv[1]
    outfile = sys.argv[2] if len(sys.argv) > 2 else None

    img = load_vga_dump(infile)

    im = Image.fromarray(img, "RGB")
    im.show()

    if outfile:
        im.save(outfile)
        print(f"Saved image to {outfile}")

if __name__ == "__main__":
    main()
