---
name: zomboid-interact-menu-tech
description: G menu (hold G) provider API, Zomboid action queue in TimedActions, SkillUnlocks for level-up card, DeathCard, SandboxScreen (UI thread, 2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T05:16:58.394Z
---

Built by UI on claude/project-thread-j1d6bj (2026-10-31), from [[zomboid-ui-pz-pieces]].

- G menu: client/interact/InteractMenu. Tap G = old default chain (CarClient.interact); hold G 6 ticks = menu (CarClient.HOLD). Systems add options via InteractMenu.provider((client, player, hit, out) -> ...). Option.of / timed / no(label, why) greyed / menu(label, sub). Built-in providers: systems (lift, train, ferry, light switch, files, downed), vanilla blocks; CarClient.menu, HoistClient, NpcClient register their own. Menu ignores G key-repeat for 600 ms.
- Queue: survival/TimedActions.start queues when busy (no cap, dedup by key); cancel/move/hit clear the queue; QUEUED attachment (labels) drawn by hud/ActionHud beside the bar.
- Level-up card: hud/SkillTape (6 s card top-centre), lines from skills/SkillUnlocks (add(skill, level, line); Mechanics reads CarParts.Part.level; fallback "gets better" line per skill). SkillTape.show(skill, lv) for tests.
- Death card: survival/DeathCard (life_start, life_kills attachments, Card S2C payload) + kit/DeathCardScreen in front of vanilla DeathScreen ("Other options" goes back to it).
- Sandbox page: kit/SandboxScreen over COMMANDS' sandbox registry; pause-menu button, create-world button, /zc sandbox (SandboxClient.opener). No presets (Josiah 05:06).
- Mechanics x-ray car: MechanicsScreen.outline (VEHICLES' screen), centimetre space under a scaled pose.
- Traits UI (2026-10-31, branch j1d6bj after SURVIVAL wave 3): KitScreen transfer queue (Zomboid transfer time, Josiah picked yes 21:33): moves across containers (swap/drag/shift-click/unequip) wait 0.5 s + 0.25 s/kg (max 4), x0.5 Dextrous, x2 All Thumbs; same-container moves instant; closing the kit drops the queue. SkillsPage "Occupation and traits" box (Traits.shown chips) + occupation in the kit header; kit/FoodTips (Hunger line, Nutritionist macros); kit/SightTraits + FogRendererMixin (short_sighted x0.6, eagle_eyed x1.25) + LightmapCatsEyesMixin (0.2 night vision). SkillTape skips cards when 2+ skills rise in one frame.
