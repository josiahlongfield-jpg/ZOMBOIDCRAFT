---
name: zomboid-halo4-format
description: Halo 4 MCC .map cache format as decoded 2026-11-08 (offsets differ from Reach); tools zomboid-mod/halo/tools/h4
metadata:
  type: reference
  modified: 2026-11-08T11:10:43.526Z
---

Cloud tools (/tmp/claude-0/h4, backup /mnt/project-files/zomboid-mod/halo/tools/h4): h4.py, h4res.py, h4model.py, h4bitmap.py, h4export.py (GLB), dump.py; three.js hologram viewer cortana_holo_viewer.html + shot.js/video.js (Playwright, swiftshader). Reach base: [[zomboid-halo-reach-format]].
- Header 'head' v13. vbase u64 @0x2d8, tag index addr u64 @0x2e0. Section virt (addr,size) pairs from 0x4d4 (debug 0x2cfd0000...), file masks @0x4c4 debug, @0x4c8 RESOURCE (=resource section file base), @0x4cc tag, @0x4d0 locale. tbase = u32@0x4e0 + u32@0x4c8.
- Index (sig 'sgat' at +0x48) read at tbase+(addr-vbase); ALL OTHER tag pointers: file = X - vbase + tbase - 0x10000 (X = addr32*4+0x50000000). This -0x10000 cost an hour.
- Names: file table @0x20 (count,off,size,index), strings @0x30; both masked with u32@0x4c4. Sid: v<0x1000 direct index, else index v+0x1ab9 (bones: try +0x1ab9 first).
- play: +0xc shared files (0x108 B: mainmenu,shared,campaign), +0x18 pages 88 B (u16 salt,u8 flags,i8 codec,i16 SHARED FILE IDX,i16 idx,u32 off,comp,unc), +0x30 segments 24 B (u32 prim/sec/ter offsets, i16 prim/sec/ter page). Pages raw deflate at file u32@0x4c8 + off of the owning map. Most mesh/texture pages live in campaign.map.
- zone: +0x58 resources 68 B (tagref, +0x10 salt u16/type u8/flags u8, +0x14 ctrl size, +0x1a segment u16, +0x1c root, +0x20 fixups (ctrl off, addr; type>>28 2 ctrl,4 page), +0x38 block whose first u32 = ctrl offset in def data). Def data = data ref @+0x154 size / +0x160 addr.
- mode: same as Reach (regions 0xc, nodes 0x30 but 0x70 B each, mats 0x48, meshes 0x68 now 0x70 B, compression 0x74 bounds at +12, NODE MAPS +0xbc, resource +0xf8). Mesh: vb idx @0x18, ib idx @0x2a, rigid node i8 @0x30. Part 24 B (mat,sort,start,count,...). IB type 3 = triangle list. Skinned vertex 48 B: pos f3 @0 (0-1 in bounds), uv u16N @16, normal i16x3 @20, tangent @28, bone idx @36, weights @40.
- bitm: images +0x7c (w,h,depth, fmt @8: 11 ARGB, 14 DXT1, 16 DXT5, 38 DXN), resources +0xa8; top mip in TERTIARY page, then secondary, then primary.
- FACE/ANIM CRACKED 2026-11-09: tag 'pcaa' (PCA facial anim): +0x0 mode ref, +0x10 jmad ref, +0x24 shot count, +0x38 block of mesh entries 0x64 B (mesh idx, vcount, ranges, +0x4c sub-block 28 B: anim sid, frame offset, frame count, 0, ncomp, coef resource datum), +0x44 basis resource. Basis prim = per mesh u16x8 [comp][vertex][8], slots 0-2 = xyz delta (halo axes) with zero = value of last comp; scale ~0.84 m per unit for head (mesh5), eyelash (mesh1) per-slot x(range ratio). Coef resource: float32 [frames][ncomp]. storm_cortana pcaa = 36-pose 'facial_animation_overlay' library (16 comps).
- H4 jmad: anims block +0x9c (64 B, sid name +0x1ab9, shared blk @+52: u16 frames,u8 nodes,u8 type), resource groups +0x1e4 (12 B count,datum). Members 0x68 B (+8 frames u16 nodes u8, +0x10 data size, total @+0x54, data fixup @+0x60). Cinematics use codec 3 raw per-frame (janim.raw3); node bitsets after data: [static rot,trans,scale][anim rot,trans,scale] 16 B each. Halo q (x,y,z,w)->glTF (y,z,x,w), t*3.048.
- m90.map holds REAL in-engine Cortana performances: cin_m093_midnightkiss (21 shots, 13 with face), cin_m092_didact (cortana..cortana10), storm_cortana14 vin_m90 splinter vignettes (408/120/271 frames, face). Tools: h4/pcaexp.py, bodyexp.py, jm4.py; viewer applyPCA/applyAnim.
- Material shaders: 'mat ' tag +0x1c block -> +0x0 textures (24B, datum@+12), +0x18 PS constants float4 (= cb13 user_parameter_160+). DXBC blobs found by scanning m90 for 'DXBC' with sampler names in RDEF; decompile via vkd3d-compiler + spirv-cross.

## Codec 10 gameplay anims SOLVED (2026-11-09)
- Rot keys (3 x int16) are smallest-three: idx = (k2&1) + 2*(k1&1) of the dropped component (x,y,z,w order), k0&1 = dropped comp is negative; stored comps are the next three cyclically after idx, each = raw/32767/sqrt2. (janim.oq OQMODE='s3')
- Static nodes: member tail = 48B static flags + 48B anim flags + ... ; last 2*n bytes = u16 indices (n = static rot+trans+scale count) into jmad shared pools: +0x1f0 int16x4 rotations, +0x1fc float3 translations, +0x208 float scales.
- Root yaw of turns lives in movement data (sz[3]), not decoded; rotate the model instead.
- Tools: /mnt/project-files/zomboid-mod/halo/tools/h4/c10exp2.py (pkl from c10dump.py), anim_base/v7/s14.json.
