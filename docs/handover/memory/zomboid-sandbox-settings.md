---
name: zomboid-sandbox-settings
description: World settings (Sandbox page) work split, from PZ review sections 17 and 20; plan status
metadata:
  type: project
  modified: 2026-10-31T03:57:48.980Z
---

2026-10-31: the COMMANDS thread ("Commands list and world settings") owns review sections 17 and 20 of https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE: all 269 B42 sandbox settings in 12 groups plus a 13th "ZomboidCraft only" group. Josiah's rule: Zomboid's version unless he has already confirmed a change; confirmed rules show as Fixed.

Split agreed with the UI thread: COMMANDS owns the backbone (option list taken from B42's own sandbox definitions, per-world store, how each applies, /zc sandbox, client-readable registry, host-only C2S set payload, S2C sync). UI draws the page (mock: Sandbox tab of https://claude.ai/artifact/6zFda6ycGpfmpPiJGmcG2M), host can reopen it from the pause menu. Owning threads wire each setting.

Built 2026-10-31 (branch claude/project-thread-wu4jl1, merged to playtest 67b3d201): package com.zomboidcraft.sandbox. Zomboid's 269 load from data/zomboidcraft/sandbox/zomboid.json (made by tools/sandbox/gen_sandbox.py from /mnt/project-files/zomboid-mod/commands/pz-sandbox-options.json, B42.21 list from PinDrop); each starts "coming" at the Apocalypse value; owners wire with SandboxOptions.wires() (id -> toggle/number/choice(key)); ours and fixed rules in SandboxOptions.ours(). PRESETS DROPPED (Josiah 2026-10-31: would need a world redesign); each Zomboid setting defaults to its own default. World file <world>/zomboidcraft_sandbox.json; ZcConfig.save() puts baseline values back (Sandbox.unapply) so the global file is the new-world template. Sync S2C on join/change; Set C2S host-only; SandboxClient.opener for the UI page; Sandbox.forNewWorld for the create-world page. CI county test checks zc sandbox set + file. Spec table: /mnt/project-files/zomboid-mod/commands/world-settings.md. This session's network can't reach pindrop.gg/pzwiki.net; Josiah can't change network settings from his phone.
