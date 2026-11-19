import bpy

# Applies a lathe/milled ring texture to the ACTIVE object's material.
# Run in Blender: Scripting tab > Open this file > Run Script (object selected).
AXIS = 'X'  # spool's length axis in LOCAL space ('X', 'Y' or 'Z')

obj = bpy.context.active_object
mat = obj.active_material
nt = mat.node_tree
nodes, links = nt.nodes, nt.links

def get(type_, loc):
    n = next((n for n in nodes if n.type == type_), None)
    if n is None:
        n = nodes.new({'TEX_COORD': 'ShaderNodeTexCoord', 'MAPPING': 'ShaderNodeMapping',
                       'TEX_WAVE': 'ShaderNodeTexWave', 'BUMP': 'ShaderNodeBump'}[type_])
        n.location = loc
    return n

coord = get('TEX_COORD', (-900, 0))
mapping = get('MAPPING', (-700, 0))
wave = get('TEX_WAVE', (-500, 0))
bump = get('BUMP', (-250, -200))
bsdf = next(n for n in nodes if n.type == 'BSDF_PRINCIPLED')

# Mapping: flatten the length axis -> rings depend only on radius from axis
mapping.vector_type = 'POINT'
mapping.inputs['Location'].default_value = (0, 0, 0)
mapping.inputs['Rotation'].default_value = (0, 0, 0)
mapping.inputs['Scale'].default_value = tuple(0.0 if a == AXIS else 1.0 for a in 'XYZ')

# Wave: clean concentric sine rings
wave.wave_type = 'RINGS'
wave.rings_direction = 'SPHERICAL'
wave.wave_profile = 'SIN'
wave.inputs['Scale'].default_value = 200.0
wave.inputs['Distortion'].default_value = 0.0
wave.inputs['Detail'].default_value = 0.0
wave.inputs['Detail Scale'].default_value = 0.0
wave.inputs['Phase Offset'].default_value = 0.0

# Remove old links from the wave output, then wire: Object -> Mapping -> Wave -> Bump -> Normal
for l in list(links):
    if l.from_node == wave or l.to_node in (mapping, wave):
        links.remove(l)
links.new(coord.outputs['Object'], mapping.inputs['Vector'])
links.new(mapping.outputs['Vector'], wave.inputs['Vector'])
links.new(wave.outputs['Fac'], bump.inputs['Height'])
links.new(bump.outputs['Normal'], bsdf.inputs['Normal'])
bump.inputs['Strength'].default_value = 0.1
bump.inputs['Distance'].default_value = 0.01

# Brushed-aluminium look
bsdf.inputs['Base Color'].default_value = (0.8, 0.8, 0.8, 1)
bsdf.inputs['Metallic'].default_value = 1.0
bsdf.inputs['Roughness'].default_value = 0.25
print("Milled texture applied to", mat.name)
