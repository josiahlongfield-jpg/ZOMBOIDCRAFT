---
name: zomboid-hollis-tech
description: Hollis Island Quarantine Station (coast site 4) build tech, qs_ kit, Seabright ferry route, and the previews gallery rule
metadata:
  type: project
---

Paused by Josiah 2026-10-30 13:22, resumed 18:46 ("Continue").

- Hollis.java: natural place over the whole island (frame X -440..380, Z -270..270, spot 6400,6200 at 0,0), LEVEL = WATER+2, flattened to y 0. Island.build: ground, drive (z -3..3), wharf (quay face QX -412, deck y 1, PrisonFerry "HOLLIS"/"SEABRIGHT", car slip + gantry, dolphins, freight shed), processing (container wall x -300, chutes, white decon tents), HollisStation, HollisCamp, story.
- HollisStation: admin (x -272..-238, pavilion + cupola, qs_flag_q), disinfecting plant + round brick stack, surgeon's house (veranda, lantern), nurses' home, covered walk z 11..14, six brick wards A-F (x -210 + 33i, z 18..58, clerestory monitor; F ruined), stone morgue (0..16, 66..80), cemetery (-170..-30, 70..136, qs_memorial).
- HollisCamp: fence x 40..290 z -140..140 (east side breached), gravel tracks, olive tent lines, motor pool (x 70..150 z 29..43), three chain-link holding pens (x 60+24k, z 56..122), stilted isolation wards (qs_stilt, qs_rib_white, qs_louvre), field hospital tents, command post, helipad + wreck, trenches + qs_lime + shrouds, burn pit, graves.
- Kit: tools/city/hollis.py (qs_), registered in photo_textures.py after boardwalk.
- Ferry: Hollis.BELL/LANDING and Boardwalk.BELL/LANDING (fishing pier T); FerryRoutes adds the route.
- Preview: /tmp/claude-0/plan/hq/go.sh NAME CX CZ R (mesh m_hq_NAME).
- PREVIEWS RULE (applies to every build) (Josiah 2026-10-30 18:48): send him previews of each build. The thread's attachment limit is full, so they go on the gallery artifact https://claude.ai/artifact/2Zsu1YGwXQV4SRUiuATiCy (source: scratchpad gallery/index.html + img/*.jpg; add shots to SHOTS and republish).
- FLY-OVER VIDEO: /tmp/claude-0/fly/run.sh renders camera paths (paths.py) through scratchpad furn/www/fly.html (view.html with a re-callable window.frame(cam)) via /tmp/claude-0/fly.js, about 1 s a frame at 960x540, then ffmpeg stitches. Satisfies [[zomboid-video-preview-rule]] for landscape builds.
- Refs: batch 22 (309 photos, 42 folders) merged 2026-10-30 19:20; batch 23 (pc_spec23.json, steelworks depth) run via RC the same evening.
