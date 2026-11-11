---
name: zomboid-reload-rules
description: Josiah's rules for every reload animation (2026-11-04): exact start/end, gun may move mid-reload, no finger clipping, flat slaps, full FP arm, right-hand handles
metadata:
  type: feedback
---
Josiah 2026-11-04 02:55-02:59 (reloads thread), for every reload:
- Start and end gun pose (and arm) must be EXACTLY his editor pose for first and third person; in between the gun may move however needed. For pulls at the back (slides, charging handles, bolts) push the gun forward and tip its back down to make room.
- Handgun grips: no finger collision during the reload (his static poses clip slightly; never change those, fix only inside the reload).
- Any slap that sends a round in (bolt catch, cover shut): open hand AND thumb flat, then back to the rest grip.
- First person shows the whole arm (upper arm too), never cut at the elbow.
- Guns with a cocking handle on the RIGHT (L86 type): left hand does the mag from the left hip, then plants back on its grip and the RIGHT hand pulls the handle (no left-arm crossover).
- He judged the natural whole-arm solver "100% better"; joints must move together, no unnatural bends or collisions.

**Why:** these are what make a reload read as natural to the player.
**How to apply:** check every new reload against this list before sending previews. Tech in [[zomboid-reloads-tech]].
- 03:05: single rounds loaded into the RIGHT side (Winchester 1866 type): left hand stays on the gun and rolls it so the loading face points up; right hand loads the rounds; then both hands back, gun back to its pose, right hand works the lever/slide.
- 03:06: always think where the ammo goes in and what parts are touched: turn the loading side of the gun toward the loading hand, and pick the right loading and holding hands for each gun.
- 03:22: Honey Badger type (charging handle on top/back): the right hand works it after the left hand has finished the mag. Fingers must never twitch.
- 03:24: as soon as a hand finishes its loading job and goes back to the gun, it must sit EXACTLY in his set grip (position and fingers) while the other hand cocks; no grip drift before the final frame.
- 03:27: first-person lever actions happen with the gun still on its side in the loading position, then it returns to the static pose.
- 05:42 (10 notes): slaps never bend a finger backwards; handgun thumb stays exactly in its static grip spot all reload; third-person STANDING pistol/revolver reloads at chest height (not waist); always aim the loading face at the loading hand (L86 bullpup: trigger hand holds gun out + tilts mag to the left hand so the arm doesn't cross the body; 870: right arm tilts and pushes the gun forward for the left hand); revolvers in FP keep the gun's static direction, swing cylinder out, nose up to dump, level to load, speedloader taken away; Big Boar: left hand lays the gun over (bolt/chamber face up) for the loading hand, stock never tilted away in FP, stock end down in TP; American-180: right hand pulls the handle after the pan; never twist the FP loading arm or invert the elbow.
- 06:12: revolvers (S&W type): the pistol-grip (right) hand stays on the grip the WHOLE reload; only the left hand works the reload. He wants the editor to give full control of hands, fingers and every arm joint so he can adjust.
- Editor v13 (06:2x): hand keys carry fk = {cl:[shrug,fwd] (TP only, FP rig has no clavicle), sh:[raise,fwd,twist], el, tw, wr:[bend,side], f:{th:[curl,spread,roll], i/m/r/p:[curl,spread]}} in degrees, applied by engine.js Rig.userPose after the solve (forward kinematics, the hand moves with the joint). Bake must honour fk when reloads go in-game. S&W 500 v6: R REST all reload, L does cylinder, speedloader from POUCH_L, puts it away, closes.
- 06:25-06:35: every hand key time gets a key on the other hand: {k:'mid'} keys (engine handTrack/handAt) keep the motion exact and only ADD his dpx/r/curl/flat/fk on top, fading to the neighbouring keys. Views sticky. Automatic finger unclip OFF for all reloads (spec.autoFingers to re-enable): it made fingers twitch whenever he moved a hand; fingers stay exactly as posed. Editor v14.
- 07:35 AR-57 ta "twisted arm": old (plain) solver flipped forearm twist/elbow side between frames. Fixed: plain solver now dry-runs, unwraps + smooths elbow side and forearm roll (ends exact); natural unwraps twist. His AR57 db doc v347: ta switched to solver natural, his sw values stripped there (backup brita/tools/reload-editor/AR57_PDW-db-v346-backup.json). Editor v15 has a per-view "Arm" Natural/Plain switch.
- 10:46: wave-2 rules + his edit habits: [[zomboid-reload-wave2]].
