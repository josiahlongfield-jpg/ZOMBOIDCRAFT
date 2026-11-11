---
name: zomboid-reloads-tech
description: Hand-made reload animations for the 14 local Brita guns (first + third person): tooling, approach, preview location, status
metadata:
  type: project
  modified: 2026-11-03T21:40:56.368Z
---
Josiah (2026-11-03 20:56) asked for reloads modelled for all 14 posed local guns with a preview of each; he picked "Hand-made per gun" (21:01). Zomboid's own pack has no reload clips (only rifle/pistol idle/aim/fire), and the Brita/Arsenal mods ship no anims.

Tooling (copy kept in /mnt/project-files/zomboid-mod/brita/tools/reload/, fpport helpers in its fpport/):
- engine.py: keyframed gun pose (relative r/d about the grip, or abs grip/fwd/up in her model space for third person), hand keys (on gun at a px point with fingers/palm directions, on body, or rest+dpx), two-bone arm reach from the shoulder, moving parts (comps/boxes of the gun zmesh, pivot, r/d/s), grab/drop/show/seat/hide events, procedural extras (mags, shells, rounds, belt, bolt).
- specs.py: one spec per gun. vid.py renders FP|TP side by side (full speed then half speed) via page.html/run.js (three.js, headless chromium).
- Needs the posing page files (arms.json, body.json, guns/*.json) from the posing artifact, game-v2 pose files, local-pc/guns zmesh, survivor pack.
Previews: /mnt/project-files/zomboid-mod/brita/previews/reloads/reload_<gid>.mp4 (local-only data, never commit).
Limits: FP shoulder sits at the camera, so hands near the eye fill the screen; Big Boar's bolt is off-screen in FP. Nothing is in the game yet: awaiting his review of the 14 previews, then bake frames into the pose files and play them in the game.
RELOAD EDITOR (built 2026-11-03 22:5x at his ask): private artifact https://claude.ai/artifact/T7GqDNuxrnjgeSJZbEM4fL. Three views, each its OWN reload: fp, tp = third person standing (used when NOT moving), ta = third person aimed (from tpaim pose; used while MOVING, the main one other players see; his rule 22:28-22:30). Aimed (ta) default keeps the gun up in the aim the whole time (his ask 23:01); hand keys have sw = elbow swing (deg, about shoulder-wrist line, JS engine only). FP now draws the upper arm too (static trA2; he wants it in the game FP reloads as well, 23:04); hand keys also have sho = shoulder fit offset (m, view space); spec.armfit.{L,R} = {sw, sho} applies to every key of that view. Saves db doc reloads/<gid> = {json: string of {fp,tp,ta: flattened spec}, t}; read it with ArtifactData get to bake into the game. engine.js = JS port of engine.py (matches Python to 1e-5 on all 14). Kit (local-only data, published only inside that private artifact) made by export_kit.py OUTDIR; sources copied to /mnt/project-files/zomboid-mod/brita/tools/reload-editor/.
Related: [[zomboid-arms-posing-tool]], [[zomboid-brita-wave2]], [[zomboid-video-preview-rule]].

## Natural arm solver (2026-11-04)
- spec.solver='natural' (engine.js natSolve, source redit/nat.js; backup brita/tools/reload-editor/). Clavicle turns shoulder with reach (<=20deg), elbow swivel by cost (gravity, wrist straight, home pull, his style elbow, continuity, body capsules + gun clash field kit.field). Old solver unchanged when no solver key (his AR-57 uses old).
- His AR-57 habits: elbows dropped 30-45deg, gun tilted more, hand r2 ~[0,-20,20] (style_ar57.json).
- AR-15 (Bush_AR15_MOE) bottom-mag reload: reload/ar15.py; poses from posing db via to_game.py; preview previews/reloads/reload_Bush_AR15_MOE.mp4; in editor v7. He called it "100% better" (2026-11-04 02:29); natural solver is the way forward.
- 2026-11-04 02:30 he said redo the other 13 on the natural arm: export_kit.py now sets solver+style on every gun but AR57. Engine (natural mode) also pulls the gun toward a shoulder when a gun-held hand can't reach, then clamps any leftover target into reach (miss.js reports reach per gun/view). Previews reload_<gid>_v2.mp4; editor v8.
- 02:55 review pass: engine natural mode now: exact ends (ends.js checks), hand push-out of the gun + per-finger unclip (fpen.js), key 'flat' 0..1 opens the hand to the bind-pose flat hand, specs PULL() for handle room, mag_rifle rhand=True. export_kit.py now writes static trA2 (an earlier re-export had dropped the FP upper arm). Previews _v3; editor v9. Rules: [[zomboid-reload-rules]].
- 03:30 pass: finger fixes solved in a prepass and smoothed (twitch.js), grip-exact when a hand is on a plain rest key (grip.js; pistols keep only the R finger fix), Honey Badger rhand, Winchester FP lever on its side. Previews _v4, editor v11.
- Previews page (he asked 05:25, easier than project files): private artifact https://claude.ai/artifact/DcsBb1amge4bMmwf1tp9Pg, source scratchpad rvids/ (index.html + v/*.mp4); republish it with each new preview round.
