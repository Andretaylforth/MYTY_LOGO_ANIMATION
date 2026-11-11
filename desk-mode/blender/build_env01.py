# Builds desk Environment 01 as a .blend: the same scene the web plates were
# rendered from, with Camera A / Camera B matching the configurator's views.
# Web scene coordinates are y-up metres; Blender is z-up: (x, y, z) -> (x, -z, y).
import bpy, bmesh, math, sys
from mathutils import Matrix, Vector, Euler

OUT = sys.argv[sys.argv.index("--") + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
Ci = C.inverted()

def lin(c):  # sRGB hex -> linear RGBA
    f = lambda v: (v / 12.92) if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return (f((c >> 16 & 255) / 255), f((c >> 8 & 255) / 255), f((c & 255) / 255), 1)

_mats = {}
def mat(color, rough=0.6, metal=0.0, name=None, emit=None):
    key = (color, round(rough, 3), metal, emit)
    if key in _mats: return _mats[key]
    m = bpy.data.materials.new(name or "mat_%06x_r%02d%s" % (color, round(rough * 100), "_metal" if metal else ""))
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = lin(color)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emit:
        b.inputs["Emission Color"].default_value = lin(emit[0]); b.inputs["Emission Strength"].default_value = emit[1]
    m.diffuse_color = lin(color)
    _mats[key] = m
    return m

def coll(name):
    c = bpy.data.collections.new(name); scene.collection.children.link(c); return c
cRoom, cDesk, cCab, cShelf, cKb, cProps, cLight, cCam, cRef = [coll(n) for n in
    ("Room", "Desk", "Cabinets", "Shelf and print", "Keyboard and mouse", "Desk props", "Lights", "Cameras", "MYTY MAX placeholder")]

def obj(name, bm, material, c, pos, rot=(0, 0, 0), parent=None, smooth=False):
    """pos / rot are in web (y-up) coordinates; rot is a three.js XYZ euler."""
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    if smooth:
        for p in me.polygons: p.use_smooth = True
    o = bpy.data.objects.new(name, me); c.objects.link(o)
    me.materials.append(material)
    R = Euler(rot, "ZYX").to_matrix().to_4x4()  # three.js "XYZ" order == Rx * Ry * Rz
    M = Matrix.Translation(Vector(pos)) @ R
    Mb = C @ M @ Ci
    if parent is not None: o.parent = parent
    o.matrix_local = Mb if parent is not None else Mb
    return o

def box(name, w, h, d, material, x, y, z, ry=0.0, c=cProps, parent=None):
    bm = bmesh.new(); bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=(w, d, h), verts=bm.verts)  # web (w, h, d) -> blender (x=w, y=d, z=h)
    return obj(name, bm, material, c, (x, y + h / 2, z), (0, ry, 0), parent)

def cyl(name, r_bottom, h, material, x, y, z, r_top=None, seg=48, c=cProps, rot=(0, 0, 0), centre=False, caps=True):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=caps, cap_tris=False, segments=seg, radius1=r_bottom, radius2=r_bottom if r_top is None else r_top, depth=h)
    return obj(name, bm, material, c, (x, y if centre else y + h / 2, z), rot, smooth=True)

seed = 1234567
def rnd():
    global seed
    seed = (seed * 1664525 + 1013904223) & 0xFFFFFFFF
    return seed / 4294967296

WALL, DESK, MAT, CAB, ACCENT, METAL, FLOOR = 0xdcdad5, 0xf1efea, 0xc9b79c, 0xa9a59c, 0xe8743b, 0xb9bcc0, 0x8d7f6c
wallM = mat(WALL, 0.92, name="Wall")
# room
box("Wall", 8, 4, 0.1, wallM, 0, -1, -0.47, c=cRoom)
box("Floor", 10, 0.1, 8, mat(FLOOR, 0.8, name="Floor"), 0, -0.84, 1.5, c=cRoom)
box("Picture rail", 8, 0.012, 0.02, mat(WALL, 0.8), 0, 1.02, -0.41, c=cRoom)
box("Pilaster", 0.16, 4, 0.05, wallM, -1.05, -1, -0.44, c=cRoom)
# desk
box("Desk top", 1.9, 0.032, 0.84, mat(DESK, 0.55, name="Desk top"), 0, -0.032, 0, c=cDesk)
legM = mat(0xe8e6e1, 0.5, name="Desk leg")
box("Desk leg L", 0.05, 0.72, 0.05, legM, -0.9, -0.752, 0.37, c=cDesk)
box("Desk leg R", 0.05, 0.72, 0.05, legM, 0.9, -0.752, 0.37, c=cDesk)
# cabinets
cabM, knobM = mat(CAB, 0.7, name="Cabinet door"), mat(METAL, 0.3, 1, name="Knob")
for i in range(4):
    for j in range(6):
        x, y = 0.72 + i * 0.262, -0.78 + j * 0.332
        box("Door %d-%d" % (i, j), 0.254, 0.324, 0.05, cabM, x, y, -0.36, c=cCab)
        cyl("Knob %d-%d" % (i, j), 0.009, 0.012, knobM, x - 0.09, y + 0.27, -0.332, seg=20, c=cCab, rot=(math.pi / 2, 0, 0), centre=True)
box("Cabinet carcass", 1.07, 2.1, 0.3, mat(CAB, 0.8, name="Cabinet carcass"), 1.113, -0.8, -0.53, c=cCab)
# shelf, books, print
box("Shelf", 0.62, 0.022, 0.17, mat(0xd8c7a8, 0.6, name="Shelf"), -0.48, 0.6, -0.335, c=cShelf)
bookC = [0x2f3b52, 0xe9e4d8, 0xb5533c, 0x8c8f8a, 0xd9c9a4, 0x34443c]
x = -0.74
for i in range(9):
    w = 0.018 + rnd() * 0.022; h = 0.15 + rnd() * 0.07
    box("Book %d" % (i + 1), w, h, 0.12, mat(bookC[i % 6], 0.75), x + w / 2, 0.622, -0.345, c=cShelf); x += w + 0.002
box("Shelf box", 0.1, 0.07, 0.1, mat(METAL, 0.35, 0.9), -0.3, 0.622, -0.34, c=cShelf)
box("Print frame", 0.3, 0.4, 0.016, mat(0x1c1c1c, 0.5, name="Frame"), 0.22, 0.52, -0.412, c=cShelf)
box("Print paper", 0.26, 0.36, 0.004, mat(0xf3efe6, 0.9, name="Paper"), 0.22, 0.54, -0.402, c=cShelf)
box("Print colour block", 0.12, 0.17, 0.003, mat(ACCENT, 0.8, name="Accent"), 0.22, 0.635, -0.399, c=cShelf)
# desk mat, keyboard, mouse
box("Desk mat", 0.66, 0.003, 0.29, mat(MAT, 0.95, name="Desk mat"), 0.36, 0, 0.2, c=cKb)
kb = bpy.data.objects.new("Keyboard", None); cKb.objects.link(kb); kb.empty_display_size = 0.05
kb.matrix_world = C @ (Matrix.Translation((0.3, 0.003, 0.2)) @ Euler((0, 0.04, 0), "ZYX").to_matrix().to_4x4()) @ Ci
box("Keyboard case", 0.335, 0.016, 0.125, mat(0xe9e6df, 0.5, name="Keyboard case"), 0, 0, 0, c=cKb, parent=kb)
capA, capB, capC = mat(0xf4f2ec, 0.6, name="Keycap light"), mat(0xa8a69f, 0.6, name="Keycap grey"), mat(ACCENT, 0.55, name="Keycap accent")
for r in range(5):
    for c_ in range(15):
        if r == 4 and 3 < c_ < 10:
            if c_ == 4: box("Spacebar", 0.123, 0.009, 0.0185, capA, -0.154 + 6.5 * 0.0213 + 0.011, 0.016, -0.045 + r * 0.0225, c=cKb, parent=kb)
            continue
        edge = c_ == 0 or c_ == 14 or r == 4
        m = capC if (r == 0 and c_ in (0, 14)) else capB if edge else capA
        box("Key %d-%d" % (r, c_), 0.0185, 0.009, 0.0185, m, -0.154 + c_ * 0.0213 + 0.005, 0.016, -0.045 + r * 0.0225, c=cKb, parent=kb)
bm = bmesh.new(); bmesh.ops.create_uvsphere(bm, u_segments=40, v_segments=24, radius=0.5)
bmesh.ops.scale(bm, vec=(0.062, 0.104, 0.034), verts=bm.verts)
obj("Mouse", bm, mat(0xf2f0ea, 0.45, name="Mouse"), cKb, (0.6, 0.016, 0.22), smooth=True)
# props
cyl("Mug", 0.041, 0.092, mat(0xeeeae2, 0.35, name="Mug"), 0.72, 0, -0.12)
cyl("Coffee", 0.036, 0.004, mat(0x2a1a12, 0.2, name="Coffee"), 0.72, 0.086, -0.12)
cyl("Pen cup", 0.038, 0.1, mat(CAB, 0.6), 0.84, 0, -0.24)
for i in range(5):
    px = 0.84 + (rnd() - 0.5) * 0.04; pz = -0.24 + (rnd() - 0.5) * 0.04
    rx = (rnd() - 0.5) * 0.35; rz = (rnd() - 0.5) * 0.35
    cyl("Pen %d" % (i + 1), 0.004, 0.17, mat([0x1c1c1c, ACCENT, 0xd9c9a4, 0x2f3b52, 0xf3efe6][i], 0.5), px, 0.115, pz, seg=10, rot=(rx, 0, rz), centre=True)
box("Notebook bottom", 0.21, 0.012, 0.29, mat(0xe9e4d8, 0.85), -0.66, 0, 0.16, 0.22)
box("Notebook top", 0.2, 0.01, 0.28, mat(0x2f3b52, 0.8), -0.665, 0.012, 0.155, 0.12)
cyl("Pencil", 0.004, 0.14, mat(0x1c1c1c, 0.4), -0.6, 0.027, 0.16, seg=12, rot=(math.pi / 2, 0, 0.5), centre=True)
box("Metal box", 0.2, 0.075, 0.15, mat(METAL, 0.38, 0.85, name="Metal box"), -0.62, 0, -0.2, -0.1)
cyl("Dial", 0.03, 0.02, mat(0xe9e6df, 0.4), -0.62, 0.075, -0.2, r_top=0.034)
lampM = mat(0xf1efea, 0.4, name="Lamp")
LX, LZ = -0.86, -0.26
cyl("Lamp base", 0.075, 0.014, lampM, LX, 0, LZ)
cyl("Lamp stem", 0.008, 0.5, lampM, LX, 0.014, LZ)
cyl("Lamp shade", 0.1, 0.13, mat(0xf6f1e6, 0.7, name="Lamp shade"), LX, 0.56, LZ + 0.06, r_top=0.05, centre=True, caps=False)

# where the live MYTY MAX stands in the configurator (wireframe only, never rendered)
ph = box("MYTY MAX (placeholder, not rendered)", 0.345, 0.415, 0.089, mat(0x0a0192, 0.5, name="Placeholder"), -0.08, 0, -0.06, math.radians(-14), c=cRef)
ph.display_type = "WIRE"; ph.hide_render = True; ph.show_name = True

# lights: a big soft window on the left + cool sky fill from the world
ld = bpy.data.lights.new("Window", "AREA"); ld.shape = "RECTANGLE"; ld.size = 1.6; ld.size_y = 1.35
ld.color = lin(0xfff1dc)[:3]; ld.energy = float(sys.argv[sys.argv.index("--") + 2]) if len(sys.argv) > sys.argv.index("--") + 2 else 130
lo = bpy.data.objects.new("Window light", ld); cLight.objects.link(lo)
wpos = C @ Vector((-2.3, 1.225, 0.7)); wtgt = C @ Vector((0, 0.1, 0))
lo.location = wpos; lo.rotation_euler = (wtgt - wpos).to_track_quat("-Z", "Y").to_euler()
world = bpy.data.worlds.new("Sky fill"); scene.world = world; world.use_nodes = True
bgn = world.node_tree.nodes["Background"]; bgn.inputs["Color"].default_value = lin(0xcfdcf2)
bgn.inputs["Strength"].default_value = float(sys.argv[sys.argv.index("--") + 3]) if len(sys.argv) > sys.argv.index("--") + 3 else 0.28

# cameras: exactly the web plates' cameras (vertical field of view, 3:2)
def camera(name, pos, tgt, fov):
    cd = bpy.data.cameras.new(name); cd.sensor_fit = "VERTICAL"; cd.sensor_height = 24.0; cd.angle_y = math.radians(fov)
    cd.clip_start = 0.05; cd.clip_end = 30
    o = bpy.data.objects.new(name, cd); cCam.objects.link(o)
    p, t = C @ Vector(pos), C @ Vector(tgt)
    o.location = p; o.rotation_euler = (t - p).to_track_quat("-Z", "Y").to_euler()
    return o
camA = camera("Camera A (View A)", (0.72, 0.52, 1.78), (0.06, 0.2, -0.1), 32)
camB = camera("Camera B (View B)", (-1.05, 0.34, 1.35), (0.02, 0.2, -0.1), 36)
scene.camera = camA
# frame 1 = View A, frame 2 = View B (timeline markers switch the active camera)
scene.frame_start, scene.frame_end = 1, 2
for f, cam, n in ((1, camA, "View A"), (2, camB, "View B")):
    mk = scene.timeline_markers.new(n, frame=f); mk.camera = cam
scene.frame_set(1)

r = scene.render
r.resolution_x, r.resolution_y, r.resolution_percentage = 2400, 1600, 100
r.engine = "CYCLES"; scene.cycles.samples = 256; scene.cycles.use_denoising = True
r.image_settings.file_format = "JPEG"; r.image_settings.quality = 90
r.filepath = "//env01_"
scene.unit_settings.system = "METRIC"
try: scene.view_settings.view_transform = "AgX"; scene.view_settings.look = "AgX - Medium High Contrast"
except Exception: pass
bpy.ops.wm.save_as_mainfile(filepath=OUT, compress=True)
print("saved", OUT, len(bpy.data.objects), "objects")
