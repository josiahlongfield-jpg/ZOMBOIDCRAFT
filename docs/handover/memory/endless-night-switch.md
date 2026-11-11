---
name: endless-night-switch
description: Josiah 2026-11-09 07:51 STRICT: all Reach/Halo/Tarkov/zombie work moves to Endless Night repo, file store and Modrinth instance
metadata:
  type: feedback
---
Josiah 2026-11-09 07:51/07:52: immediately switch; ANY future Reach-world work (incl. Halo, Cortana, Tarkov, zombies) uses the new routes only.
- Instance: Modrinth profile ENDLESS NIGHT; never write to MEDIEVAL GLOOM (1) (frozen county world).
- Project files: /mnt/project-files/endless-night/<area>/ (Cortana: endless-night/cortana/). Old zomboid-mod/ stays readable.
- PC big assets stay in C:\Dev\Zomboidcraft-Desktop\additions\ (never OneDrive).
- Repo josiahlongfield-jpg/ENDLESS-NIGHT (mod id endlessnight); ZOMBOIDCRAFT frozen. Wait for the repo thread's first green build before committing there.
**Why:** Endless Night split from the county world. **How to apply:** rules in /mnt/project-files/endless-night/SWITCH.md.
- 08:27 2026-11-09 Josiah: ALL threads hold off pushing anything until the 'Move Endless Night to its own repo' thread has finished and prepped everything; keep work local/patches.
- 09:33 2026-11-09 push hold LIFTED: ENDLESS-NIGHT repo ready (branch off main, merge into playtest; mod id endlessnight, com.endlessnight.*, config/endlessnight/, commands /en /enhud). County world, cars, loot, survival needs, skills, aircraft, trains, companions, Harvestman, command net removed.
