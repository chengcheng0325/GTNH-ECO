"""Build the E-Storage Drive Bay (GTNH ECO) 3D block preview.

Runs headless: blender -b -P tools/drive_bay_build.py
Deliverables:
  models/drive_bay_preview.blend   - editable engineering source
  tools/drive-bay-preview.png      - 768x768 isometric Cycles render (RGBA)
  models/drive_bay_preview.glb     - glTF 2.0 binary backup

Design (16-unit Minecraft block, origin centered, front face = +Y):
  - Outer shell: dark graphite #343A44 (real mod texture color)
  - Front face is a recessed panel framed by a lighter metal bezel #525A66,
    with #707A88 machined highlight strips (per storage_array_drives_front.png)
  - One large recessed slot (68.75% wide x 36.9% tall) cut into the panel,
    inner walls very dark #14171C (hollow, not flat)
  - Gold contact strip on the slot floor + cyan glowing strip on the back wall
  - Green LED strip on the TOP face + two small green LEDs flanking the slot
"""
import bpy
import math
import os
from mathutils import Vector

WS = r"D:\DeepSeek\GTNH-ECO"
BLEND = os.path.join(WS, "models", "drive_bay_preview.blend")
PNG = os.path.join(WS, "tools", "drive-bay-preview.png")
GLB = os.path.join(WS, "models", "drive_bay_preview.glb")

SIZE = int(os.environ.get("D_PREVIEW_SIZE", "768"))
SAMPLES = int(os.environ.get("D_PREVIEW_SAMPLES", "64"))

# ----------------------------------------------------------------------------
# helpers
# ----------------------------------------------------------------------------
def add_box(name, center, size, mat):
    """Cube primitive -> real world-size box in mesh data space, scale=1."""
    bpy.ops.mesh.primitive_cube_add()
    obj = bpy.context.active_object
    obj.name = name
    dims = obj.dimensions
    obj.scale = (size[0] / dims[0], size[1] / dims[1], size[2] / dims[2])
    bpy.ops.object.transform_apply(scale=True)
    obj.location = center
    if mat is not None:
        obj.data.materials.append(mat)
    return obj


def make_mat(name, base, metallic, rough, emit=None, emit_strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (base[0], base[1], base[2], 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough
    if emit is not None:
        bsdf.inputs["Emission Color"].default_value = (emit[0], emit[1], emit[2], 1.0)
        bsdf.inputs["Emission Strength"].default_value = emit_strength
    return m


def aim_at(loc, target=(0, 0, 0)):
    d = Vector(target) - Vector(loc)
    return d.to_track_quat("-Z", "Y")


def add_area_light(name, loc, energy, color, size=(12, 8)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.color = color
    data.size = size[0]
    data.size_y = size[1]
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = aim_at(loc)
    return obj


# ----------------------------------------------------------------------------
# scene reset (user asked for a clean factory scene)
# ----------------------------------------------------------------------------
for obj in list(bpy.data.objects):
    bpy.data.objects.remove(obj, do_unlink=True)
for coll in list(bpy.data.collections):
    if coll.users == 0:
        bpy.data.collections.remove(coll)

scene = bpy.context.scene
scene.render.engine = "CYCLES"

# ----------------------------------------------------------------------------
# materials (colors taken from the actual mod textures)
# ----------------------------------------------------------------------------
MAT_SHELL = make_mat("MAT-Shell-Graphite", (0.204, 0.227, 0.267), 0.55, 0.42)
MAT_CAVITY = make_mat("MAT-Cavity-Inner", (0.078, 0.090, 0.110), 0.15, 0.68)
MAT_FRAME = make_mat("MAT-Frame", (0.322, 0.353, 0.400), 0.70, 0.34)
MAT_FRAME_LIGHT = make_mat("MAT-FrameHighlight", (0.439, 0.478, 0.533), 0.75, 0.28)
MAT_GOLD = make_mat("MAT-Contacts-Gold", (0.910, 0.722, 0.294), 1.0, 0.22,
                    emit=(1.0, 0.78, 0.30), emit_strength=1.6)
MAT_CYAN = make_mat("MAT-CyanAccent", (0.302, 0.765, 1.0), 0.4, 0.30,
                    emit=(0.302, 0.765, 1.0), emit_strength=3.0)
MAT_LED = make_mat("MAT-LED-Green", (0.03, 0.10, 0.06), 0.0, 0.30,
                   emit=(0.10, 1.0, 0.30), emit_strength=2.2)

# ----------------------------------------------------------------------------
# geometry
# ----------------------------------------------------------------------------
# --- shell body: 16x16 cube, front face recessed to y=7.33 (panel) ----------
body = add_box("GEO-Bay-Body", (0, -0.335, 0), (16, 15.33, 16), MAT_SHELL)

# pocket cutter: slot opening on the front panel
#   x in [-5.5, 5.5]  (11.0 / 16 = 68.75 % width)
#   z in [-2.95, 2.95] (5.9 / 16 = 36.9 % height)
#   y in [3.8, 7.55]   (depth 3.55, cut through the panel face)
cutter = add_box("Cutter-Pocket", (0, 5.675, 0), (11.0, 3.75, 5.9), None)
bm = body.modifiers.new("Pocket", "BOOLEAN")
bm.operation = "DIFFERENCE"
bm.solver = "EXACT"
bm.object = cutter
bpy.context.view_layer.objects.active = body
bpy.ops.object.modifier_apply(modifier="Pocket")
bpy.data.objects.remove(cutter, do_unlink=True)

# bevel outer + panel + slot rim edges for the machined look
bvm = body.modifiers.new("EdgeBevel", "BEVEL")
bvm.width = 0.22
bvm.segments = 2
bvm.limit_method = "ANGLE"
bvm.angle_limit = math.radians(30)
bpy.ops.object.modifier_apply(modifier="EdgeBevel")

# tag the 5 inner pocket walls with the dark cavity material
mesh = body.data
mesh.materials.append(MAT_CAVITY)
cav_idx = mesh.materials.find("MAT-Cavity-Inner")
assert cav_idx > 0, "cavity material not attached"
n_tag = 0
for p in mesh.polygons:
    c = p.center
    if abs(c[0]) < 5.6 and abs(c[2]) < 3.1 and 3.6 < c[1] < 6.5:
        p.material_index = cav_idx
        n_tag += 1
print("cavity faces tagged:", n_tag)

# drop the stray empty material slot the boolean merge leaves behind
mesh.materials.clear()
mesh.materials.append(MAT_SHELL)
mesh.materials.append(MAT_CAVITY)
for p in mesh.polygons:
    c = p.center
    if abs(c[0]) < 5.6 and abs(c[2]) < 3.1 and 3.6 < c[1] < 6.5:
        p.material_index = 1

# --- front bezel frame (lighter metal rim around the recessed panel) --------
fz, fy = 7.1, 7.35  # frame inner z half-extent, panel plane
for name, center, size in (
    ("GEO-Bay-Frame-Top", (0, 7.675, 7.55), (16, 0.65, 0.9)),
    ("GEO-Bay-Frame-Bottom", (0, 7.675, -7.55), (16, 0.65, 0.9)),
    ("GEO-Bay-Frame-Left", (0, 7.675, 0), (0.9, 0.65, 14.2)),
    ("GEO-Bay-Frame-Right", (0, 7.675, 0), (0.9, 0.65, 14.2)),
):
    add_box(name, center, size, MAT_FRAME)
bpy.data.objects["GEO-Bay-Frame-Left"].location = (-7.55, 7.675, 0)
bpy.data.objects["GEO-Bay-Frame-Right"].location = (7.55, 7.675, 0)

# --- #707A88 machined highlight strips on the frame inner bands -------------
add_box("GEO-Bay-Highlight-Top", (0, 7.72, 7.32), (13.6, 0.34, 0.42), MAT_FRAME_LIGHT)
add_box("GEO-Bay-Highlight-Bottom", (0, 7.72, -7.32), (13.6, 0.34, 0.42), MAT_FRAME_LIGHT)

# --- gold contact strip on the slot floor (bottom of the pocket) ------------
add_box("GEO-Slot-Contacts", (0, 5.9, -2.73), (8.8, 2.6, 0.42), MAT_GOLD)

# --- cyan glowing strip on the pocket back wall -----------------------------
add_box("GEO-Slot-CyanStrip", (0, 3.86, 2.1), (6.4, 0.08, 0.9), MAT_CYAN)

# --- top green LED strip (on the +Z top face) -------------------------------
add_box("GEO-LED-TopStrip", (0, 0, 8.11), (6.6, 1.3, 0.16), MAT_LED)

# --- two small green LEDs flanking the slot on the front panel --------------
add_box("GEO-LED-FrontA", (4.60, 7.42, 2.85), (0.85, 0.12, 0.85), MAT_LED)
add_box("GEO-LED-FrontB", (4.60, 7.42, -2.85), (0.85, 0.12, 0.85), MAT_LED)

# ----------------------------------------------------------------------------
# camera + lights
# ----------------------------------------------------------------------------
cam_data = bpy.data.cameras.new("CAM-Preview")
cam_data.lens = 30
cam = bpy.data.objects.new("CAM-Preview", cam_data)
scene.collection.objects.link(cam)
cam.location = (18.5, 18.5, 13.2)
cam.rotation_mode = "QUATERNION"
cam.rotation_quaternion = aim_at(cam.location, (0, 0, -0.4))
scene.camera = cam

add_area_light("LGT-Key", (20, 15, 18), 2600, (1.0, 0.97, 0.92), (14, 9))
add_area_light("LGT-Fill", (-18, 10, 6), 800, (0.86, 0.91, 1.0), (10, 7))
add_area_light("LGT-Rim", (-14, -16, 14), 1000, (0.80, 0.90, 1.0), (12, 8))

pl = bpy.data.lights.new("LGT-PocketFill", "POINT")
pl.energy = 25
pl.color = (1.0, 0.95, 0.85)
plobj = bpy.data.objects.new("LGT-PocketFill", pl)
scene.collection.objects.link(plobj)
plobj.location = (0, 5.3, 0)

world = bpy.data.worlds.new("World-Preview")
world.use_nodes = True
bg = world.node_tree.nodes.get("Background")
bg.inputs[0].default_value = (0.08, 0.09, 0.11, 1.0)
bg.inputs[1].default_value = 0.35
scene.world = world

# ----------------------------------------------------------------------------
# render settings
# ----------------------------------------------------------------------------
scene.render.resolution_x = SIZE
scene.render.resolution_y = SIZE
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.view_settings.view_transform = "Standard"
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.cycles.samples = SAMPLES
if hasattr(scene.cycles, "use_denoising"):
    scene.cycles.use_denoising = True
elif hasattr(scene.cycles, "denoiser"):
    scene.cycles.denoiser = "OPENIMAGEDENOISE"

# try GPU, fall back to CPU
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for dt in ("OPTIX", "CUDA", "HIP"):
        try:
            prefs.compute_device_type = dt
            prefs.get_devices()
            if any(d.type == dt for d in prefs.devices):
                for d in prefs.devices:
                    d.use = True
                scene.cycles.device = "GPU"
                print("cycles device: GPU", dt)
                break
        except Exception:
            continue
    else:
        scene.cycles.device = "CPU"
        print("cycles device: CPU")
except Exception as e:
    scene.cycles.device = "CPU"
    print("cycles device: CPU (", e, ")")

# ----------------------------------------------------------------------------
# outputs
# ----------------------------------------------------------------------------
os.makedirs(os.path.dirname(BLEND), exist_ok=True)
os.makedirs(os.path.dirname(PNG), exist_ok=True)
os.makedirs(os.path.dirname(GLB), exist_ok=True)

bpy.ops.wm.save_as_mainfile(filepath=BLEND)
print("saved blend:", BLEND)

scene.render.filepath = PNG
bpy.ops.render.render(write_still=True)
print("rendered:", PNG)

# select only the GEO- meshes for a clean GLB
for obj in scene.objects:
    obj.select_set(obj.name.startswith("GEO-"))
try:
    bpy.ops.export_scene.gltf(filepath=GLB, export_format="GLB", use_selection=True)
except Exception as e1:
    print("gltf export retry after enabling addon:", e1)
    bpy.ops.preferences.addon_enable(module="io_scene_gltf2")
    bpy.ops.export_scene.gltf(filepath=GLB, export_format="GLB", use_selection=True)
print("exported glb:", GLB)

# ----------------------------------------------------------------------------
# summary
# ----------------------------------------------------------------------------
# purge unused factory materials for a clean .blend
for m in list(bpy.data.materials):
    if m.users == 0:
        bpy.data.materials.remove(m)
print("=== SCENE SUMMARY ===")
for o in scene.objects:
    if o.type == "MESH":
        print("%-22s tris=%-6d mats=%s" % (o.name, sum(len(t.vertices) - 2 for t in o.data.polygons) if o.data.polygons else 0, [m.name if m else None for m in o.data.materials]))
print("materials:", [m.name for m in bpy.data.materials])
bb = [body.matrix_world @ Vector(c) for c in body.bound_box]
mn = Vector((min(v[i] for v in bb) for i in range(3)))
mx = Vector((max(v[i] for v in bb) for i in range(3)))
print("body world bounds: min=%s max=%s size=%s" % (
    tuple(round(v, 2) for v in mn), tuple(round(v, 2) for v in mx),
    tuple(round(mx[i] - mn[i], 2) for i in range(3))))
