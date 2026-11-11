---
name: zomboid-character-screens
description: Endless Night character selection mock-ups (Reach style), how the Noble figures are posed, and HUD-in-game install status
metadata:
  type: project
  modified: 2026-11-08T19:12:03.479Z
---
Latest mock-ups: round 5 (2026-11-08 20:31) in relaxed-v5/ (cs2-noble-team.jpg, cs2b-noble-team-jorge.jpg only; hover clip refused, storage full). Josiah 19:55: do NOT shrink figures, keep round-3 size and make cards fit (wider cards). Carter has DMR (19:23); hovered card may grow/glow; nothing may spill past a border. Older rounds idle-v2/v3/v4 deleted with his OK 20:30. Other screens (cs1,cs3-5) = round-1 copies at character-screens/ root. Previews posted in thread "Review character selection screens"; UI thread renders. APPROVED for in-game build by Josiah 2026-11-08 20:37 ("Looks good put that in"); handed to UI thread. IN PLAYTEST JAR 21:26 (CI green); not yet seen in game by Josiah. Not wired: Armory picks do not change figure; Halo loadout/operator face saved but no gear effect (Halo weapons not in game). BUILT: SoldierScreen (client/hud) in playtest 2211e5e, jar 21:26; it opens instead of the marine screen when the charsel kit is present. Kit zip parts (charsel/reach/tarkov .zip.001...) are in the profile config/zomboidcraft/hud and unpack themselves on launch; backup in additions/halo-reach/hud/kits. Builder is cs/charkit.py (copy in zomboid-mod/halo/tools). NOT yet seen in game. Gaps: the Armory doesn't change the figure; loadout and operator face are recorded only (soldier.json).
RULE (Josiah 19:22/19:23): characters and guns NEVER spill outside their card border; the hovered card may grow bigger and more pronounced, figure grows inside it. Carter carries the DMR (19:23). 19:55: DON'T shrink figures to fit; keep round-3 size (470 px tall, ~590 hovered) and make the cards fit them (front-facing camera eye (0,0.12,1), Jun/Jorge (0.25,0.12,1)).

Josiah's asks so far: figures in an idle pose holding a weapon (12:34); then a MORE RELAXED idle and every Noble holding their SIGNATURE gun, Jorge as his real model with the M247H (18:55).

How round 3 is built:
- Relaxed pose = campaign AI "alert:rifle:idle" (rifle lowered), Kat "alert:pistol:idle" from the female graph, Jorge "alert:turret:idle".
- Poses come from the delivered animation GLBs in additions\halo-reach\animations (spartan_ai part01, spartan_female, spartan_jorge). MCC Reach has NO mainmenu.map, so there are no lobby poses.
- Bodies are the real m20 Noble variants (additions\halo-reach\noble\spartan_*.glb, true colours). John uses the cloud olive sp_john117, because the m20 john117 renders white.
- Jorge's gun is the carried weapon additions\halo-reach\vehicles\vehicles__human__turrets__machinegun__weapon__machinegun_turret.glb on b_r_grip.
- Weapons: Carter DMR, Kat magnum, Jun sniper, Emile shotgun, Six DMR, John AR.
- Tools are in zomboid-mod/halo/tools: poseglb.py, posed.py, rendernoble3.py, charscreens.py.

HUD: Reach Spartan HUD and Tarkov operator HUD are merged into playtest (Josiah approved the in-game clip at 12:34).
- DONE 21:00 as zip parts (see above). Was: copy the HUD kits (/mnt/project-files/zomboid-mod/halo/hud-kits/{reach,tarkov}) into his config/zomboidcraft/hud/.
- Then back them up to C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\hud.
- Without them the old field kit shows. `/zchud spartan|operator|classic|reload` picks the style.

**Why:** Josiah paused all development 2026-11-08 12:41. Only the character-screen previews resumed (18:55). His PC came back on at 19:05.
**How to apply:** only preview work until he says to build; see [[zomboid-halo-maps]], [[zomboid-purge-direction]].

NEW ASKS (Josiah 2026-11-08 23:47-23:50):
- FULL ARMORY: every Reach armour piece swappable for the "YOU" (Noble Six) AND the bonus character (John), the same as the real game. The other Nobles stay fixed.

- Armour file details (pmcg layout, sources, Haunted): [[zomboid-reach-armory-tech]].
- Main menu (layout, toggle, slideshow, code): [[zomboid-main-menu]]. 00:49 "Same goes for tarkov": operator gets a Poses panel + full customisation too.
MAIN MENU IN PLAYTEST 01:32 (efd38a1, CI green). NEEDS charsel kit copied to his PC: /mnt/project-files/zomboid-mod/halo/hud-kits/charsel-zip/charsel.zip -> profile config/zomboidcraft/hud/charsel.zip (game unpacks; without it old title shows). PC offline at 01:32; UI thread copies when back. F1 on menu = vanilla title. Subtitle shows the CONTINUE save, not the toggle.
ARMORY v2 mock-ups posted 2026-11-09 07:48 (previews/armory/armory-v2-{helmet,john,colours,emblem,nameplate,pose,operator,operator-headwear}.jpg, reach-poses-21.jpg). MCC names; 225 FG/105 BG emblems; ~600 nameplates; 21 MCC poses; all unlocked (asked about rank locks). Operator: USEC/BEAR, Tarkov slots; decision card: kit slots looks-only vs starting gear (rec starting gear). Awaiting Josiah. Review thread hit 24-attachment cap; coordinator asked for a new preview thread.
