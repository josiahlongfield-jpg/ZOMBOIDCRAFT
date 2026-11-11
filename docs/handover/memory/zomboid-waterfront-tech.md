---
name: zomboid-waterfront-tech
description: Grayhaven Waterfront tech - Waterfront/Terminal/Pier/Boats classes, wf_ kit, Ferris wheel member blocks, preview + checks
metadata:
  type: project
---

Waterfront landmark (island east shore), BUILT 2026-10-29 (branch d61kbn). The island's last landmark. See [[zomboid-landscape-queue]].
- Layout.java `waterfront` lot: 2x3 pitches, waterSide 1, quay = x1-x0-Waterfront.SEAWALL (harbour east of x 36). LandmarkSite variant "waterfront".
- Waterfront.java (Grounds): seawall/promenade, Anchor Plaza, marina floats MA=136/MB=176, harbormaster/bait huts, dry stack, boats, story. WaterfrontTerminal.java: Harbor Landing terminal, clock tower, ferry "Calder Queen" (turned 45,0,62,3). WaterfrontPier.java: Pier 9 (deck z 92..120 to x 120), 8 shops (north clams/taffy/gifts/arcade/tees, south scoops/surf/chowder), arch with wf_pier_ board, carousel at (81,114), Ferris wheel hub (102,17) R15 gondola plane z 106, rims z105/107, legs z103/109. Boats.java small craft.
- Ferris wheel blocks (tools/city/waterfront.py `member()`): wf_rim_K / wf_rim_lit_K / wf_spoke_K / wf_leg_K, K = direction in 22.5 deg steps from +x toward +y, suffix _p/_m = 6 px off centre along the left normal. LESSON: a positive model rotation about z turns +x toward +y (the broadcast tv_diag comment says otherwise). Rings use a midpoint circle (rounded-angle sampling makes double cells = parallel dashes). WaterfrontPier.ring()/line() pick K and offset per cell.
- Refs: refs/city/waterfront-pier-ferris-wheel.
- Preview: /tmp/claude-0/plan/wf/go.sh SEED [mesh] (SitePreview ... landmark waterfront 133 213; SitePreview floods the harbour for variant waterfront). Mesh m_wf_mSEED. Dump = lot +8, ground dump y12. Wheel face cam: cam=104,22,72,110,27,114,60.
- Checks clean seeds 1-3. Known benign: jump pockets on terminal wall sills (thin glass), flat roofs of single-storey huts/dry stack with no stairs, 4 bow vessel_rail orientcheck hits.
- Litter rule: nothing solid in the boardwalk rows along shop fronts or the row by the terminal gate (bags blocked a door once).
