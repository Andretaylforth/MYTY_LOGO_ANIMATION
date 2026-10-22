"""Add a realistic hair particle system to the horse.

Usage
-----
Inside Blender: open this file in the Text Editor and press "Run Script".

From the command line (saves the .blend in place):
    blender your_scene.blend --background --python add_horse_hair.py

The horse is found automatically: the first mesh with a vertex group named
"hair" (objects with "horse" in the name win). Set HORSE_OBJECT to force one.
The "hair" vertex group drives both where hair grows and how long it is, so
the existing weight painting carries straight through to the result.

Re-running the script replaces the hair system instead of stacking another.
"""

import bpy

# --- Settings --------------------------------------------------------------

HORSE_OBJECT = ""          # leave empty to auto-detect
VERTEX_GROUP = "hair"
SYSTEM_NAME = "Horse_Hair"

PARENT_COUNT = 3000        # guide strands
CHILDREN_RENDER = 60       # interpolated children per guide at render time
CHILDREN_VIEWPORT = 8      # ... and in the viewport

# Hair length as a fraction of the horse's longest dimension. On a ~2.4 m
# horse 0.15 gives ~36 cm at full weight; lower weights in the vertex group
# shorten the strands.
LENGTH_FACTOR = 0.15
HAIR_LENGTH = None         # absolute length in object units, overrides factor

# Coat colour (Principled Hair BSDF, melanin model).
# Bay/brown: 0.85 / 0.55   Chestnut: 0.6 / 0.9   Black: 1.0 / 0.2   Grey: 0.15 / 0.1
MELANIN = 0.85
MELANIN_REDNESS = 0.55

USE_DYNAMICS = False       # enable hair physics so it sways with the animation


# --- Helpers ---------------------------------------------------------------

def set_first(target, value, *names):
    """Set the first attribute that exists; property names moved between versions."""
    for name in names:
        if hasattr(target, name):
            setattr(target, name, value)
            return


def set_input(node, value, *names):
    for name in names:
        socket = node.inputs.get(name)
        if socket is not None:
            socket.default_value = value
            return


def find_horse():
    if HORSE_OBJECT:
        obj = bpy.data.objects.get(HORSE_OBJECT)
        if obj is None:
            raise RuntimeError(f"No object named '{HORSE_OBJECT}'")
        return obj

    candidates = [o for o in bpy.data.objects
                  if o.type == 'MESH' and VERTEX_GROUP in o.vertex_groups]
    if not candidates:
        raise RuntimeError(f"No mesh has a vertex group named '{VERTEX_GROUP}'")
    candidates.sort(key=lambda o: "horse" not in o.name.lower())
    return candidates[0]


def remove_existing(obj):
    for mod in [m for m in obj.modifiers if m.type == 'PARTICLE_SYSTEM']:
        if mod.particle_system.name == SYSTEM_NAME:
            settings = mod.particle_system.settings
            obj.modifiers.remove(mod)
            if settings.users == 0:
                bpy.data.particles.remove(settings)


def hair_material():
    mat = bpy.data.materials.get(SYSTEM_NAME) or bpy.data.materials.new(SYSTEM_NAME)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    out.location = (400, 0)

    hair = nodes.new("ShaderNodeBsdfHairPrincipled")
    hair.location = (0, 0)
    hair.parametrization = 'MELANIN'
    set_input(hair, MELANIN, "Melanin")
    set_input(hair, MELANIN_REDNESS, "Melanin Redness")
    set_input(hair, 0.30, "Roughness")
    set_input(hair, 0.35, "Radial Roughness")
    set_input(hair, 0.10, "Coat")
    set_input(hair, 0.15, "Random Color")      # strand-to-strand colour variation
    set_input(hair, 0.20, "Random Roughness")
    set_input(hair, 1.55, "IOR")

    links.new(hair.outputs[0], out.inputs["Surface"])
    return mat


# --- Main ------------------------------------------------------------------

def add_hair():
    obj = find_horse()
    remove_existing(obj)

    size = max(obj.dimensions) / max(max(obj.scale), 1e-6)  # local-space size
    length = HAIR_LENGTH if HAIR_LENGTH else size * LENGTH_FACTOR

    mod = obj.modifiers.new(SYSTEM_NAME, 'PARTICLE_SYSTEM')
    psys = mod.particle_system
    psys.name = SYSTEM_NAME
    psys.seed = 7
    psys.vertex_group_density = VERTEX_GROUP
    psys.vertex_group_length = VERTEX_GROUP

    s = psys.settings
    s.name = SYSTEM_NAME
    s.type = 'HAIR'
    s.use_advanced_hair = True
    s.count = PARENT_COUNT
    s.hair_length = length
    s.hair_step = 10
    s.display_step = 4
    s.render_step = 6

    # Emission: evenly jittered over faces so there are no bald patches.
    s.emit_from = 'FACE'
    s.use_emit_random = True
    s.distribution = 'JIT'
    s.use_even_distribution = True

    # Strand direction: follow the surface normal with a slight lay-down and
    # random spread so it doesn't look like a brush.
    s.normal_factor = 1.0
    s.tangent_factor = 0.15
    s.tangent_phase = 0.0
    s.factor_random = 0.08
    s.brownian_factor = 0.0
    s.effector_weights.gravity = 1.0

    # Interpolated children give the volume; clump + roughness break it up.
    s.child_type = 'INTERPOLATED'
    set_first(s, CHILDREN_VIEWPORT, "child_percent", "child_nbr")
    set_first(s, CHILDREN_RENDER, "rendered_child_count")
    s.virtual_parents = 0.0
    s.child_length = 1.0
    s.child_length_threshold = 0.25           # a quarter of the children vary in length
    s.child_radius = 0.2
    s.child_roundness = 0.5

    s.clump_factor = 0.45
    s.clump_shape = -0.25
    set_first(s, 0.6, "clump_noise_size")

    s.roughness_1 = 0.012                     # uniform frizz
    s.roughness_1_size = 1.2
    s.roughness_2 = 0.06                      # random stray strands
    s.roughness_2_size = 1.5
    s.roughness_2_threshold = 0.85
    s.roughness_endpoint = 0.03               # loose tips
    s.roughness_end_shape = 1.2

    s.kink = 'WAVE'                           # gentle natural wave
    s.kink_amplitude = length * 0.04
    s.kink_frequency = 2.2
    s.kink_shape = 0.0
    s.kink_flat = 0.0

    # Strand shape: horse hair is coarse and tapers to a fine tip.
    s.root_radius = 1.0
    s.tip_radius = 0.0
    s.radius_scale = 0.003
    s.shape = -0.1
    set_first(s, True, "use_close_tip")

    # Material
    mat = hair_material()
    if mat.name not in obj.data.materials:
        obj.data.materials.append(mat)
    s.material = list(obj.data.materials).index(mat) + 1
    set_first(s, mat.name, "material_slot")

    # Optional physics so the mane and tail move with the animation.
    if USE_DYNAMICS:
        psys.use_hair_dynamics = True
        cloth = psys.cloth.settings
        cloth.quality = 6
        cloth.mass = 0.3
        cloth.bending_stiffness = 0.6
        cloth.bending_damping = 0.5
        cloth.air_damping = 1.0
        set_first(psys.cloth.settings, 0.3, "pin_stiffness")

    # Make sure strands render as round 3D curves.
    scene = bpy.context.scene
    if hasattr(scene, "cycles_curves"):
        set_first(scene.cycles_curves, 'THICK', "shape")
    render = scene.render
    set_first(render, 'STRIP', "hair_type")
    set_first(render, 3, "hair_subdiv")

    print(f"[{SYSTEM_NAME}] added to '{obj.name}': {PARENT_COUNT} guides x "
          f"{CHILDREN_RENDER} children, length {length:.3f}, "
          f"vertex group '{VERTEX_GROUP}'")
    return obj


add_hair()

if bpy.app.background and bpy.data.filepath:
    bpy.ops.wm.save_mainfile()
