---
name: zomboid-reach-pipeline
description: How Reach maps are pulled from Josiah's PC, converted in the cloud and shipped back; where outputs live; storage limits
metadata:
  type: project
---
- Source: C:\XboxGames\Halo- The Master Chief Collection\Content\haloreach\maps (44 maps, READ ONLY). Maps >400 MB pre-split in C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\split.
- Cloud tools in /tmp/claude-0/halo (session-only; backup in /mnt/project-files/zomboid-mod/halo/tools/): wpipe.py (join+convert queue), wexport.py, lexport.py, lmat.py (texlib dedup), wpack2.py, feeder.py, packwatch.py, cj.py/cw.py (commit cycle).
- PC output: C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\world\<map>\ = <bsp>.glb(.partNN) + <map>_collision.zip + <map>_props.zip(.partNN) + manifest.json; textures in world\tex\ (first 21) and world\texpacks\tex_*.zip. Loader must join .partNN and unzip.
- Cloud keeps compact layout copy (collision npz, manifest, placements) in /tmp/claude-0/halo/layout/<map>/ - lost when the container goes.
- Limits: commit reads <=25 MB/file (chunk 23.5 MB), 50 files/call; stage <=400 MB/file, 500 MB/call; project storage ~350 MB free (transit cap 250 MB); cloud disk ~2-4 GB free. Fresh transit files can 404 for ~a minute: only commit files older than 60 s.
- Never write to OneDrive folders; ask before any >1 GB file.
Related: [[zomboid-reach-world-direction]], [[zomboid-halo-maps]].
- STATUS 2026-11-08 21:04: ALL 42 levels converted and shipped to the PC (geometry, collision, props, ~50 texpacks). Fixed m10/m35/m70 failed/lost pieces and m45 collision (resource type 4). m45_020/025/090 are empty space volumes, not lost geometry. Firefight maps' duplicate BSPs are listed in each map's dup.json pointing at the campaign copy. PC m45 has an empty m45_collision.zip; the real one is m45_collision.zip.part00/01.
- Cloud still holds layout/ (306 MB, all 42 levels) + raw m45.map/campaign.map/shared.map/20_sword_slayer.map in /tmp/claude-0/halo - only while this container lives.
- SQUADS (2026-11-08 22:10): scnr +0x398 squads (108 B: name 32 B; spawn pts blk @0x3c (0x6c B, pos f3 @0x14 halo units, yaw @0x24); formations blk @0x30 (pos @0x10); cells blk @0x54 (0x6c B: count i16 @0x10, chars blk @0x14 and weapons blk @0x20 (16 B, palette idx i16 @0xc), vehicle idx i16 @0x46)). Palettes: chars +0x3ec, vehicles +0x138, weapons +0x168 (16 B, tag datum @+12). Tool worldlay/squads.py; done m45,m10,m20 -> /mnt/project-files/zomboid-mod/halo/squads/. ALL CAMPAIGN DONE 2026-11-09 07:30 (m30 163, m52 196, m35 156, m50 245, m70 157, m60 239 squads); still optional: ff* firefight waves.
- BACKDROP LAND: real far landscapes are render GLBs <map>_000.glb on PC (collision of backdrops is sparse/useless). Rendered 12 km windows: worldlay/bdrender.py -> ov/land_<m>.png. m30 and m35 share one backdrop and coordinates (neighbours); m20 and m60 are the SAME valley (summer/winter).
- NEXT PC SESSION PLAN: /mnt/project-files/zomboid-mod/halo/plans/reach-world-next-pc-session.md (Josiah's PC was on a timer, off 22:10 2026-11-08). Land renders backed up in zomboid-mod/halo/land/.
- 2026-11-09 11:47 FULL PULL for offline work: all campaign maps (m05 m10 m20 m30 m35 m45 m50 m52 m60 m70 m70_bonus) + campaign.map + shared.map in /tmp/claude-0/halo/maps/keep/ (size-verified; m05/m30/shared are symlinks into /mnt/user-data/uploads). Container-only: lost if the container resets. PC world listing + game log in /mnt/project-files/endless-night/reach-world/pc/.
