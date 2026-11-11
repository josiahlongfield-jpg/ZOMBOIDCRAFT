---
name: tarkov-grip-holds-tech
description: Tarkov foregrip/handguard left-hand holds (GripPose) in previews and in game; SV-98 stock fix; Josiah's 2026-11-07 grip rule
metadata:
  type: project
  modified: 2026-11-07T02:29:20.921Z
---

STRICT RULE (Josiah 2026-11-07 05:36): ALL foregrip attachments must be used (held) whenever fitted to a weapon, including a grip put onto a weapon in game that didn't have one before (bench fitting must switch the hold to the fitted grip's own).
RULE (Josiah 2026-11-07 02:04/02:09): every gun's left hand must sit on its foregrip properly, and a grip LOOTED in game must actually be held by the player model. Scopes-as-loot bench work must cover grips too, each grip bringing its own hold.

How: Tarkov foregrip/handguard (and the SV-98 chassis stock) prefabs carry a left-hand skeleton "Base HumanLPalm 1" (wrap, used) and "Base HumanLPalm". Foregrip wins over handguard.
- Preview viewer (scratch web/index.html): gripPoses()/gripHand() run at the end of pose() unless GRIPIK===false. Weight = how far the palm is from where the IDLE has it on that part (near 2.5 cm, far 6 cm); the old palm-to-hold distance weight dragged hands off magazines in reloads (Josiah 03:58 "ghost animations"). Raw Tarkov FK already holds the mags.
- Variant: foregrips and grip-handguards (MP5K kurtz) use " 1" (wrap); plain forends (SV-98 chassis) use "" (cradle); " 1" on a forend bent the wrist 90 deg.
- web/holds.js exports per gun: {grip, guard, holds{meshBone:{palm[7] rel to part's LOD0 mesh bone, fingers{Digit:q}}}} into b1/holds/*.json; merged into local guns/tarkov_*.json (grip, guard, holds).
- Game: client/operator/GripHold.java (commit 3ffd453a8), called in OperatorView after the clip blend; LocalGuns.Spec has holds/grip/guard + Hold class. Checked against the viewer with a javac harness (/tmp/claude-0/jt): palm+fingers exact, elbow within 8 mm (no bend-goal pole in game).
- exp2.js now bakes RAW clips (cfg.grip true to bake holds); the game applies holds live.
- Guns with holds: 870 (RVG + FAB guard), AK-102 (RK-1 + B-10M), SVDS (SIG vertical), MDR (Stubby), MP5K (Kurtz guard), SV-98 (chassis forend). PM, Glock, VAL, MP-18 have none.
- KIT RULE (fixed 04:2x): every mod root q = FIX * its stored root q (old rule inverse(LOD0) broke the TA51 ACOG mount on the MDR = ACOG hidden + ADS flipped, the SV-98 chassis hold, the M12B stock; MP-18 barrel root stores -90X). Old b1 jsons backed up in b1/b1bak.
- CAMERA (Josiah 03:58 "guns too far"): game OperatorView draws the rig x/y-widened by tan35/tan25 (= Tarkov default FOV 50; MC hand FOV is 70) and ADS puts the eye ON mod_aim_camera (pack ads still bakes it 10 cm ahead; code adds EYE back). OperatorHands same widen. Previews: vid2 FOV 50, ads.js GAMEAIM = game's exact hold maths (rig turned about the aim point, sight centred). Commit 7fabf835c.

PREVIEWS prev6 (all 10, after the 03:58/04:04 fixes) = artifact HcDKWUhTAqykmA8GUPzxHN, mp4s in /mnt/project-files/zomboid-mod/tarkov/previews/batch1/; 7fabf835c CI green; awaiting his OK to merge into playtest.

Next: part visibility per fitted part + bench fitting for grips (fitted grip currently = spec.grip).

Related: [[tarkov-weapon-batch1]], [[tarkov-assets-pipeline-tech]].
