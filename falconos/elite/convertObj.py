import struct

materials = {
    "lambert2SG": 0xDF,
    "lambert3SG": 0xC8,
    "lambert4SG": 0xFF
}

def write_array(f, fmt, items):
    f.write(struct.pack("<i", len(items)))
    for item in items:
        f.write(struct.pack(fmt, *item))

def export_model(obj_path, out_path, color=0xEF):
    vertices = []
    faces = []

    scale = 0.01

    with open(obj_path) as f:
        for line in f:
            if line.startswith("usemtl"):
                name = line.split()[1]
                if name in materials:
                    color = materials[name]
                else:
                    print(f"Unknown material: {name}, using default color")
                    color = 0xff

            elif line.startswith("v "):
                _, x, y, z = line.split()
                vertices.append((float(x)*scale, float(y)*scale, float(z)*scale))

            elif line.startswith("f "):
                parts = line.split()[1:]
                idx = [int(p.split("/")[0]) - 1 for p in parts]
                # already triangulated
                faces.append((idx[0], idx[1], idx[2], color))

    with open(out_path, "wb") as f:
        write_array(f, "<fff", vertices)
        write_array(f, "<iiii", faces)

    print(f"Wrote {len(vertices)} vertices, {len(faces)} faces")

export_model("../../../Downloads/Spaceship/spaceship.obj", "models/spaceship.dat")