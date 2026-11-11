---
name: zomboid-halo-reach-format
description: Halo Reach MCC .map cache format as decoded by our cloud reader (offsets for tags, names, resource pages, BSP collision, instances)
metadata:
  type: reference
---

- READER (cloud, /tmp/claude-0/halo: reach.py, rres.py, rcoll.py; copy kept in /mnt/project-files/zomboid-mod/halo/tools):
  - Header: u64 @0x2e0 = vbase, u64 @0x2e8 = tag index addr. Section 0 (debug) size u32 @0x4d0 = resource section file base. Resource section size u32 @0x4e8. Tag section file base = @0x4d0 + @0x4e8.
  - Tag address = addr32*4 + 0x50000000; file = that - vbase + tagbase. Index header +0 groups (count, addr64), +0x10 tags (8 B: group i16, salt, addr32).
  - Tag names: file = (virtual + u32 @0x4cc) & 0xffffffff, for the file table @0x24 and file index @0x2c (count @0x20).
  - play tag: +0x18 pages (88 B: salt, flags, codec, shared-file idx, idx, offset, comp, uncomp; codec 0 = raw deflate), +0x3c segments (16 B: prim page, sec page, prim off, sec off). shared idx 2 = shared.map.
  - zone tag: +0x64 resources (64 B: owner tagref, +0x10 salt, type u8, flags; +0x14 control offset, +0x18 control size, +0x22 segment, +0x24 root, +0x28 fixups (8 B: ctrl offset, addr)). Definition data = data ref @+0x148 (size) / @+0x154 (addr). Fixup addr type >>28: 2 = ctrl, 4 = primary page.
  - sbsp resource type 5 root: +0 collision bsps, +0x18 instance definitions (324 B, collision bsp blocks at +0x8). The collision bsp has 9 blocks (12 B each); 6 = surfaces (12 B: plane u16, first edge u16, material i16), 7 = edges (6 x u16), 8 = vertices (16 B).
  - Type 6 root +0x48 = instances (156 B: scale, forward, left, up, position, def i16 @+0x34).
  - Sword Base (MP map) holds every MP weapon (assault_rifle, dmr, magnum, needle_rifle, focus_rifle, ...) and objects\characters\spartans\spartans (bipd + hlmt).

- MODELS (2026-11-08, rmodel.py/export.py/rbitmap.py/rshader.py/glb.py/viewglb.py): mode +0xc regions (16 B: name sid, perms 24 B = name, mesh i16, count u16, 4x u32 instance masks), +0x1c instance mesh idx, +0x20 instance placements (60 B: name, node i32, scale, 3x3, pos), +0x30 nodes (0x60 B: name, parent i16 @4, t @0xc, quat xyzw @0x18 standard), +0x48 materials (0x2c B, shader datum @0xc), +0x68 meshes (0x5c B: parts @0 (24 B mat,sort,start i32,count i32,...), subparts @0xc (16 B start,count,part,vc), vb idx 8xh @0x18, ib idx @0x28, rigid node i8 @0x2e, vtype @0x2f), +0x74 compression (bounds @+4), +0xb0 per-mesh node maps (u8 lists), +0xf8 resource datum.
  - Geometry resource (type 1): root+0x18 vertex bufs, root+0x24 index bufs (fixup ptrs). VB: count u32, type u16, stride u16, size, data ptr fixup @+0x14. Stride 44/36: pos float3 0..1 in compression bounds, uv u16N @16, normal short3N @20, tangent @28, (44) bone idx u8x4 @36 + weights u8x4 @40 (map via node map). Index type 5 = strip with degenerates.
  - Shader (rm**) +0x38 properties -> +0x10 textures (24 B, bitmap datum @0xc; first = base map). bitm +0x7c images (0x30 B: w,h,depth, fmt u16 @8: 14 DXT1 16 DXT5 38 DXN), +0xa8 resource datums; top mip in resource SECONDARY page.
  - String ids (set 0): index < 0x4ca direct, else index + 0x128b in the string table (count @0x30, table @0x34, index @0x3c, masked like file names).
  - campaign maps need maps\campaign.map (447 MB, over the 400 MB stage cap): split on his PC by Remote Control into additions\halo-reach\split\campaign.map.part1/2, join in the cloud.
  - Spartans: armor add-ons are instances in one mesh, turned on per permutation mask. Elite/Grunt/Jackal/Hunter/Jorge/Noble heads all in m20.map.

Related: [[zomboid-halo-maps]].

- COLOURS: object change colours at bipd +0x134 (4 ch x 24 B, initial perms 32 B: weight, lower rgb, upper rgb, variant sid @+28). Bake tex x prod lerp(1,col_k,cc[k]); DXT1 cc maps have alpha 255, zero it. hlmt +0x84 variants (56 B), regions @+0x14 (24 B), perms @+8 (36 B), '' = none.
- DELIVERY: commit cap 25 MB/file, 50/call; via zomboid-mod/halo/transit, delete after.
