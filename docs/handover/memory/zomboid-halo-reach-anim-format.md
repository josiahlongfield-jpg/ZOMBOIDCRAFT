---
name: zomboid-halo-reach-anim-format
description: Halo Reach jmad (animation) format 2026-11-08: layout, curve codec 9 rotation + translation keys SOLVED; tools in zomboid-mod/halo/tools
metadata:
  type: project
  modified: 2026-11-08T09:30:02.454Z
---
Tools: /tmp/claude-0/halo/jmad.py, janim.py, fk.py, rseq.py, vid.py (back up to /mnt/project-files/zomboid-mod/halo/tools). Part of [[zomboid-halo-maps]], format base [[zomboid-halo-reach-format]].
- m20 graphs: spartans 13199 (82 nodes; most anims ref spartans_ai), spartans_ai 9891 (locomotion), elite_ai 8045, grunt 8583, jackal 8947, hunter 9102, spartans_female_ai 10483.
- jmad tag: +0x48 skeleton (0x2c B, sid name; same order as mode nodes), +0x94 animations (60 B: name sid; @0x28 datum+@0x2c i16 = ref into another jmad; @0x30 block of 1 shared-data elem), +0x1ac resource groups (12 B: member count, resource datum).
- shared data: u16 nodes, u16 frames (that order), u8 type(1 base,3 overlay), @0x20 u16 group, u16 member.
- resource members 0x64 B: @8 u16 frames,u16 nodes, @0xc sizes [static, animated, staticflags, animflags, movement, per-frame movement 12B/frame].
- data = static codec + animated codec + 3 static flag bitsets + 3 animated bitsets (rot,trans,scale), each max(sf,af)/3 bytes; node = i-th set bit.
- static codec 1: u8 rc,tc,sc; rot int16x4/32767 @0x20, trans 3f @u32[0xc], scale f @u32[0x10].
- codec 9 CURVE: hdr 0x20 ('WANG'), u32 rot offsets rel 0x20; trans table at 0x20+u32@0xc.
  - rot track: 52 39(3a Covenant) K(u16) flag ..; flag0: K frame-delta bytes then (K+1) 6-B keys interleaved with K 4-B tangents; flag1: 6-B key every frame.
  - ROT KEY SOLVED: u,v,a=int16/32767; n=normalize(u,v,sign(a)*sqrt(1-u²-v²)) (sign of a = sign of hidden z); w=1-2|a|; s=sqrt(1-w²); q=(-n*s,w). Absolute local rot, Halo xyzw like mode defaults. (Older guess with -sqrt z and s*sign(a) was wrong for a>0: twisted legs.)
  - trans record: 54 xx K(u16), 5 floats from +4: f0(low byte=flag), base xyz, range; then K deltas, (K+1) 3xint16 keys interleaved with K 3-B tangents. pos=base+int16/32767*range.
- Interp: slerp/lerp between keys (tangents unused, 0x77=constant guess). Halo->glTF: q(x,y,z,w)->(y,z,x,w), t*3.048 (x,y,z)->(y,z,x).
- Validated: idle feet planted at rest height 0.048 every frame; run/walk alternate. Preview zomboid-mod/halo/previews/reach-anim-test.mp4 (Carter, 2026-11-08).
- Josiah 09:36: Noble use Halo's OWN animations (not Tarkov).
- Codec 3 = 8-B quantized aim/look overlays, not decoded. Scale tracks not decoded.
- Next after anims (coordinator 09:29): Reach vehicles with full dropship animations + Covenant cruisers ([[zomboid-purge-direction]]).
- DELIVERED 2026-11-08 10:45 to additions\halo-reach\animations (glTF GLBs: mesh + clips, play in 3D Viewer/Blender): spartan_player (1410, 5 parts), spartan_ai/Noble (954, 4 parts), spartan_female (276; also refs spartan_ai), spartan_jorge 157, elite_player 1281 (3 parts), elite (AI) 1131 (3 parts), grunt 402, jackal 438, hunter 178, carter/jorge/jun/kat head faces, first-person\ {spartan,elite}_fp_<weapon> x48 (fp arms + weapon mesh on r_hand). clip_index.json lists every clip per file. 7,532 clips.
- Each map holds only PART of a graph's clips: merged all 44 maps into /tmp/claude-0/halo/clipdb/*.pkl (11,335 clips; clipdb.py, proc.sh stages<=400MB maps, joinproc.sh joins RC-split parts of m10/m35/m45/m50/m60/m70 in additions\halo-reach\split). Some maps store a clip with EMPTY data: skip those.
- device_commit_files reads at most 25 MB per file: animchunk.py splits into _partNN under 23.5 MB.
- SHELVED (Josiah 10:43): Brutes, Drones (buggers), Marines, civilians etc. on hold; revisit later only if he wants them.
- Not exported: codec 8 (3 'first_person:pitch_and_turn' clips), root motion (clips play in place), extra graphs present: brute 411, bugger 245, marine 1082, civilians, engineer, mule, moa.
- type 2 = overlay (aim/look, codec 3 raw per-frame), type 3 = replacement (grip/reload); exported absolute, tagged [overlay]/[replace].
