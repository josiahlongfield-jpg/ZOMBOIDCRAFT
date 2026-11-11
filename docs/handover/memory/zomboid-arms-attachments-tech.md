---
name: zomboid-arms-attachments-tech
description: Foregrips and optics in the arms posing tool (v19, 2026-11-03): part data, how they mount, front hand on a grip, ADS through an optic
metadata:
  type: project
  modified: 2026-11-03T06:14:50.672Z
---

Josiah asked 2026-11-03 for "foregrips and scopes" in the posing tool (https://claude.ai/artifact/Ur4ZP18eXgMUDALKpCEhyZ, v19). Related: [[zomboid-arms-posing-tool]], [[zomboid-brita-irons-tech]].

- Parts (v31, Josiah 08:46 removed the Brita bipod and ALL his first pack's att_* grips+sights): Brita 6 grips + M203/GP-25, plus the 15 sp_* sights. Removed files in scratchpad tool/parts-off. Output tool/web/parts/<id>.json (metres, gun frame x right/y up/z back, origin = mount point) + parts/index.json.
- Pack parts sized from real products.
- Sight line: per-optic "Sight line height fix" slider (mm, P.optics[id].dy) shared by every gun.
- P.att[gun] = {grip, gcm, optic, ocm, oy, ocmBy}; v30 (08:10): optic forward/back is per gun per SIGHT (ocmBy[id], falls back to class key dot/low/long, then default). v29 RULE (08:09): ADS eye stays at the gun's irons eye point whatever the optic/position; the slider moves the sight, not the gun (only the 6 cm push moves the gun). P.gripHand[grip][pose] = front hand on that grip, all guns.
- ADS with an optic: its line replaces the irons; red dots keep the irons' eye point, magnified ones put the eye 8 cm behind the eyepiece. Glass hidden in the eye view. (v20: any fitted optic removes the irons, see below.)
- v20 (2026-11-03 06:4x, Josiah): guns drawn with their real game paint and ink outline. RULE: a fitted scope/sight takes the irons off: (irons.py NOIRONS=1 builds the no-irons guns.)
- SIGHTS DIRECTION (Josiah 2026-11-03 06:47): forget the Brita scopes/sights (solid glass, sight pictures not made for first person); guns use ONLY his bought pack's sights (att_holo, att_red_dot, att_mini_dot, att_prism_4x) for now. More packs coming; bring each in like the grips. Rule (06:40): every sight must sit on the rail and its dead centre must be exactly on the crosshair. mount x = the gun's own centreline (irons rear x: M14 7.7, BOAR 7.4, M2 7.75, L86 7.9 px, others 8), not 8.
- v21 (2026-11-03 ~07:10): Brita optic parts deleted from the page. opticMount x = midX (irons rear x), y = railTop. Grips also centred on midX. All 48 pairs test 0.00 px off. v22 RULE (Josiah 06:55 'it needs to show'): ADS keeps rear sight/optic back >= 6 cm from the eye (adsPush slides gun+arms back along the sight line); game port must do the same.
- NEW SIGHT PACK RULES (Josiah 07:02, 07:04): pistols AND shotguns get ONLY the FastFire red dot and EFLX red dot (plus irons), EXCEPT slug shotguns (07:04) which may also take slightly magnified sights (read as low power, up to 4x); SMGs get no rifle scopes; assault rifles (Honey, M14, L86) max 8x (07:49); American-180 red dots/holos + irons only (08:53); crossbow max 4x (10:39). Pack zip on his Desktop (3.9 GB); models+colour maps in additions\sightpack.
- v24 (07:2x): 15 sp_* sights from that pack (scratchpad sp/build_sights.py; centre = reticle mesh middle; RT6/Vudu sit in his Burris PEPR cantilever). Left out: DeltaPoint 45 offset, Geissele RMR mount, standalone Burris. No slug shotguns yet (870 CQB = non-slug); ask when more shotguns come. 157 pairs 0 px.
- v25 (07:4x): red dots default back on the receiver (their back face at the irons' rear sight); scopes keep 1/4 of sight radius. PiP RULE for the game port: mag>1 eyepiece (index eyepiece [z,r]) shows world at fov/mag, pack reticle (texture, main=black etched, red kept) fills it, edge vignette; outside world half-res + mild blur, hands/gun sharp; old lens-size ring unused with optics. Lit reticles (red dots, holo, piggyback) drawn red in 3D. Vudu has no pack reticle: drawn SR-1 style. 'Copy all (this view)' button copies every group + attachments.
- v27 RULE (07:53 'same sight picture at 6cm as at 15cm'): 1x sights in the eye view only are drawn shortened along the sight line about their back face by eyeDist/0.15 m (shrinkOptic); game port must do the same in first person.
