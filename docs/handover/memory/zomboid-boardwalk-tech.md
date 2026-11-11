---
name: zomboid-boardwalk-tech
description: Seabright Boardwalk (coast group site 3) build tech: natural place, shops/arcade/boardwalk/beach + amusement pier (wheel, carousel, swing, booths, wooden coaster), sb_ kit
metadata:
  type: project
---

Built 2026-10-30 by LANDSCAPE (coast-and-industry group, see [[zomboid-landscape-queue]]). Refs: refs/coast (boardwalk-arcade, boardwalk-amusements, beach-pier, lifeguard-tower).

- Boardwalk.java: natural Place like [[zomboid-cape-tech]]; Layout special-cases spot id "boardwalk" (4550,6050), Builders dispatches LANDMARK variant "boardwalk". Frame x -60..440 (east = sea), z -210..210, LEVEL = WATER+5 (68). Ocean Ave x 2..12, shops x 22 (back) .. 56 (front, facing east), boardwalk deck x 57..74, beach east of it as it lies (sand laid), plaza z -32..32, pier x 75..262 z -28..28, fishing pier z -5..5 out to 424 with a T and the bait shop.
- The plan's `pier boardwalk 200,0 950,0 20` line is only read for PORT places; the boardwalk ignores it.
- BoardwalkShops: shop(z0,z1,key,wall,neon,cols) with stepped false front, pilasters, awning, neon; fitOut per key; Funland arcade z 40..100 (claw rows, skee lanes facing west, pinball, cabinets, prize wall), 12x3 neon + sb_marquee bulbs.
- BoardwalkPier: Ferris wheel (wf_ members, WX 100 WZ -17), carousel (100,15), swing ride (140,-12) with Round.wall lhr drum + Round.roof tinred "smm" crown, booths x 122..162, the Nor'easter coaster: two runs in planes z -22 (out, lift k=1 to y 24) and -6 (back: drop k=2, camelback), sb_track_K members via line(), sb_track_z at the turns, bents every 4 x at z+-1 with X braces; station x 168..186.
- Kit: tools/city/boardwalk.py (sb_ textures + ~270 blocks incl. neon pictures sb_neon_KEY_I(_on), signs, marquee), registered in photo_textures.py after cape. Facing rule: sb_rail stands on the edge it faces (like wf_rail); axis blocks (sb_beam, sb_fence) model along z.
- Preview: /tmp/claude-0/plan/bw/go.sh a 4740 6050 252 (mesh m_bw_a); local = frame + (70, 22 for y, 258).
- PREFIX LESSON: bw_ is the harbour breakwater kit (ports.py, which deletes all bw_ keys on rerun); boardwalk renamed to sb_ (Seabright). Check blocks.json prefix counts before naming a new kit.
