"""Readers for the prop packs' meshes: glTF (.gltf and .glb), Wavefront OBJ and binary FBX.

Every reader returns a list of Part: world-space triangles (n, 3, 3) in the file's units with y up,
their texture coordinates (n, 3, 2), the base colour image (h, w, 4 floats, or None), a colour factor
(r, g, b, a in 0..1) and the part's node or material name.
"""
import io
import json
import re
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None


@dataclass
class Part:
    tris: np.ndarray
    uvs: np.ndarray
    image: np.ndarray | None
    factor: tuple
    name: str


def _image(data):
    return np.array(Image.open(io.BytesIO(data)).convert("RGBA"), np.float32)


# ---------------------------------------------------------------- glTF

_CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
_NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def _matrix(node):
    if "matrix" in node:
        return np.array(node["matrix"], np.float64).reshape(4, 4).T
    t = np.array(node.get("translation", [0, 0, 0]), np.float64)
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    s = np.array(node.get("scale", [1, 1, 1]), np.float64)
    r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def load_gltf(path, only=None):
    """All mesh primitives under the default scene; `only` keeps nodes whose name matches (regex),
    and everything below them."""
    path = Path(path)
    raw = path.read_bytes()
    if raw[:4] == b"glTF":
        n = struct.unpack("<I", raw[12:16])[0]
        doc = json.loads(raw[20:20 + n])
        off = 20 + n
        buffers = [raw[off + 8:off + 8 + struct.unpack("<I", raw[off:off + 4])[0]]]
    else:
        doc = json.loads(raw)
        buffers = [(path.parent / b["uri"]).read_bytes() for b in doc.get("buffers", [])]

    def view(i):
        bv = doc["bufferViews"][i]
        start = bv.get("byteOffset", 0)
        return buffers[bv.get("buffer", 0)][start:start + bv["byteLength"]], bv.get("byteStride", 0)

    def accessor(i):
        a = doc["accessors"][i]
        data, stride = view(a["bufferView"])
        dt, nc = _CT[a["componentType"]], _NC[a["type"]]
        size = np.dtype(dt).itemsize * nc
        start = a.get("byteOffset", 0)
        if stride and stride != size:
            buf = np.frombuffer(data, np.uint8)
            idx = start + np.arange(a["count"])[:, None] * stride + np.arange(size)
            return buf[idx].copy().view(dt).reshape(a["count"], nc)
        return np.frombuffer(data, dt, count=a["count"] * nc, offset=start).reshape(a["count"], nc)

    images = {}

    def image(tex_index):
        src = doc["textures"][tex_index]["source"]
        if src not in images:
            im = doc["images"][src]
            if "bufferView" in im:
                images[src] = _image(view(im["bufferView"])[0])
            else:
                images[src] = np.array(Image.open(path.parent / im["uri"]).convert("RGBA"), np.float32)
        return images[src]

    parts = []

    def walk(i, parent, keep):
        node = doc["nodes"][i]
        m = parent @ _matrix(node)
        keep = keep or only is None or bool(re.search(only, node.get("name", "")))
        if "mesh" in node and keep:
            for prim in doc["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                pos = accessor(prim["attributes"]["POSITION"]).astype(np.float64)
                uv = (accessor(prim["attributes"]["TEXCOORD_0"]).astype(np.float64)
                      if "TEXCOORD_0" in prim["attributes"] else np.zeros((len(pos), 2)))
                ind = accessor(prim["indices"]).ravel().astype(np.int64) if "indices" in prim else np.arange(len(pos))
                world = (np.c_[pos, np.ones(len(pos))] @ m.T)[:, :3]
                if np.linalg.det(m[:3, :3]) < 0:
                    ind = ind.reshape(-1, 3)[:, ::-1].ravel()
                img, factor, name = None, (1, 1, 1, 1), node.get("name", "")
                if "material" in prim:
                    mat = doc["materials"][prim["material"]]
                    pbr = mat.get("pbrMetallicRoughness", {})
                    factor = tuple(pbr.get("baseColorFactor", [1, 1, 1, 1]))
                    if "baseColorTexture" in pbr:
                        img = image(pbr["baseColorTexture"]["index"])
                    name = mat.get("name", name)
                parts.append(Part(world[ind].reshape(-1, 3, 3), uv[ind].reshape(-1, 3, 2), img, factor, name))
        for c in node.get("children", []):
            walk(c, m, keep)

    for r in doc["scenes"][doc.get("scene", 0)]["nodes"]:
        walk(r, np.eye(4), False)
    return parts


# ---------------------------------------------------------------- OBJ

def load_obj(path, only=None, image=None):
    """Objects (o/g) matching `only` (regex), with each material's map_Kd and Kd from the .mtl, or
    `image` (a path) over all of them when the .mtl names no texture."""
    path = Path(path)
    mats, cur = {}, None
    for lib in re.findall(r"^mtllib\s+(.+)$", path.read_text(errors="ignore"), re.M):
        mtl = path.parent / lib.strip()
        if not mtl.exists():
            continue
        for line in mtl.read_text(errors="ignore").splitlines():
            t = line.strip().split(None, 1)
            if not t:
                continue
            if t[0] == "newmtl":
                cur = mats.setdefault(t[1].strip(), {"Kd": (1, 1, 1), "map": None})
            elif cur is not None and t[0] == "Kd":
                cur["Kd"] = tuple(float(v) for v in t[1].split()[:3])
            elif cur is not None and t[0] == "map_Kd":
                cur["map"] = t[1].strip().split()[-1]
    V, VT, faces = [], [], {}
    obj, mat = "", None
    for line in path.read_text(errors="ignore").splitlines():
        if line.startswith("v "):
            V.append([float(v) for v in line.split()[1:4]])
        elif line.startswith("vt "):
            VT.append([float(v) for v in line.split()[1:3]])
        elif line.startswith(("o ", "g ")):
            obj = line[2:].strip()
        elif line.startswith("usemtl "):
            mat = line[7:].strip()
        elif line.startswith("f "):
            if only is not None and not re.search(only, obj):
                continue
            idx = []
            for p in line.split()[1:]:
                s = p.split("/")
                idx.append((int(s[0]), int(s[1]) if len(s) > 1 and s[1] else 0))
            for k in range(1, len(idx) - 1):
                faces.setdefault(mat, []).append((idx[0], idx[k], idx[k + 1]))
    V, VT = np.array(V, np.float64), np.array(VT if VT else [[0, 0]], np.float64)

    def at(arr, i):
        return arr[i - 1] if i > 0 else arr[len(arr) + i]

    parts = []
    for mat, fs in faces.items():
        tris = np.array([[at(V, a[0]) for a in f] for f in fs])
        uvs = np.array([[at(VT, a[1]) if a[1] else (0, 0) for a in f] for f in fs])
        info = mats.get(mat, {"Kd": (1, 1, 1), "map": None})
        img = np.array(Image.open(image).convert("RGBA"), np.float32) if image else None
        if info["map"] and img is None:
            for cand in (path.parent / info["map"], path.parent / Path(info["map"]).name):
                if cand.exists():
                    img = np.array(Image.open(cand).convert("RGBA"), np.float32)
                    break
        parts.append(Part(tris, uvs, img, tuple(info["Kd"]) + (1,), mat or ""))
    return parts


# ---------------------------------------------------------------- binary FBX

def _fbx_nodes(data):
    version = struct.unpack_from("<I", data, 23)[0]
    wide = version >= 7500
    head = "<QQQ" if wide else "<III"
    hsize = struct.calcsize(head)

    def prop(pos):
        t = chr(data[pos])
        pos += 1
        if t in "YCIFDL":
            fmt = {"Y": "<h", "C": "<?", "I": "<i", "F": "<f", "D": "<d", "L": "<q"}[t]
            return struct.unpack_from(fmt, data, pos)[0], pos + struct.calcsize(fmt)
        if t in "fdlib":
            n, enc, clen = struct.unpack_from("<III", data, pos)
            pos += 12
            raw = data[pos:pos + clen]
            if enc == 1:
                raw = zlib.decompress(raw)
            dt = {"f": np.float32, "d": np.float64, "l": np.int64, "i": np.int32, "b": np.bool_}[t]
            return np.frombuffer(raw, dt, count=n), pos + clen
        if t in "SR":
            n = struct.unpack_from("<I", data, pos)[0]
            return data[pos + 4:pos + 4 + n], pos + 4 + n
        raise ValueError(f"FBX property type {t!r}")

    def node(pos):
        end, nprops, _ = struct.unpack_from(head, data, pos)
        if end == 0:
            return None, pos + hsize + 1
        nlen = data[pos + hsize]
        name = data[pos + hsize + 1:pos + hsize + 1 + nlen].decode(errors="ignore")
        p = pos + hsize + 1 + nlen
        props = []
        for _ in range(nprops):
            v, p = prop(p)
            props.append(v)
        kids = []
        while p < end:
            k, p = node(p)
            if k is None:
                break
            kids.append(k)
        return (name, props, kids), end

    out, pos = [], 27
    while pos < len(data) - hsize:
        n, pos = node(pos)
        if n is None:
            break
        out.append(n)
    return out


def _find(nodes, name):
    return [n for n in nodes if n[0] == name]


def _euler(deg):
    x, y, z = np.radians(deg)
    rx = np.array([[1, 0, 0], [0, np.cos(x), -np.sin(x)], [0, np.sin(x), np.cos(x)]])
    ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    rz = np.array([[np.cos(z), -np.sin(z), 0], [np.sin(z), np.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def _model_matrix(model):
    p70 = _find(model[2], "Properties70")
    vals = {}
    for p in (p70[0][2] if p70 else []):
        if p[0] == "P" and len(p[1]) >= 7:
            vals[p[1][0].decode(errors="ignore")] = np.array(p[1][4:7], np.float64)
    t = vals.get("Lcl Translation", np.zeros(3))
    r = _euler(vals.get("PreRotation", np.zeros(3))) @ _euler(vals.get("Lcl Rotation", np.zeros(3)))
    s = vals.get("Lcl Scaling", np.ones(3))
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def load_fbx(path, image=None, only=None):
    """Every Geometry in the file as one part each, placed by its models' transforms (the file's
    own units and up axis), textured with `image` (a path) since the packs keep their textures
    beside the meshes, not inside them."""
    nodes = _fbx_nodes(Path(path).read_bytes())
    objects = _find(nodes, "Objects")
    img = np.array(Image.open(image).convert("RGBA"), np.float32) if image else None
    models = {m[1][0]: m for m in (_find(objects[0][2], "Model") if objects else [])}
    parent = {}
    for c in (_find(_find(nodes, "Connections")[0][2], "C") if _find(nodes, "Connections") else []):
        if c[1][0] == b"OO":
            parent.setdefault(c[1][1], c[1][2])

    def world(oid):
        m = np.eye(4)
        seen = 0
        while oid in models and seen < 64:
            m = _model_matrix(models[oid]) @ m
            oid = parent.get(oid)
            seen += 1
        return m

    parts = []
    for geo in (_find(objects[0][2], "Geometry") if objects else []):
        name = geo[1][1].split(b"\x00")[0].decode(errors="ignore") if len(geo[1]) > 1 else ""
        if only is not None and not re.search(only, name):
            continue
        kids = geo[2]
        verts = _find(kids, "Vertices")
        pvi = _find(kids, "PolygonVertexIndex")
        if not verts or not pvi:
            continue
        V = np.asarray(verts[0][1][0], np.float64).reshape(-1, 3)
        idx = np.asarray(pvi[0][1][0], np.int64)
        uv_layer = _find(kids, "LayerElementUV")
        UV = UVI = None
        mapping = b"ByPolygonVertex"
        if uv_layer:
            lk = uv_layer[0][2]
            UV = np.asarray(_find(lk, "UV")[0][1][0], np.float64).reshape(-1, 2)
            ui = _find(lk, "UVIndex")
            UVI = np.asarray(ui[0][1][0], np.int64) if ui else None
            mt = _find(lk, "MappingInformationType")
            mapping = mt[0][1][0] if mt else mapping
        tris, uvs, start = [], [], 0
        corner = 0
        poly = []
        for k, v in enumerate(idx):
            last = v < 0
            vi = ~v if last else v
            if UV is None:
                uvk = (0.0, 0.0)
            elif mapping == b"ByPolygonVertex":
                uvk = UV[UVI[k] if UVI is not None else k]
            else:
                uvk = UV[UVI[vi] if UVI is not None else vi]
            poly.append((vi, uvk))
            if last:
                for j in range(1, len(poly) - 1):
                    tris.append([V[poly[0][0]], V[poly[j][0]], V[poly[j + 1][0]]])
                    uvs.append([poly[0][1], poly[j][1], poly[j + 1][1]])
                poly = []
        if tris:
            m = world(parent.get(geo[1][0]))
            T = np.array(tris)
            T = (np.c_[T.reshape(-1, 3), np.ones(T.size // 3)] @ m.T)[:, :3].reshape(-1, 3, 3)
            if np.linalg.det(m[:3, :3]) < 0:
                T = T[:, ::-1]
                uvs = [u[::-1] for u in uvs]
            parts.append(Part(T, np.array(uvs), img, (1, 1, 1, 1), name))
    return parts
