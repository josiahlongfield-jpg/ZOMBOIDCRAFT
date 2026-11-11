---
name: project-split-handover
description: 2026-11-11 project split into ZomboidCraft / Endless Night: Minecraft / Unreal; where the handover packs live (docs/handover PRs) and how they were built
metadata:
  type: project
  modified: 2026-11-11T12:42:05.522Z
---
Josiah 2026-11-11 12:16: split this project into three. New projects: "ZomboidCraft" (repo ZOMBOIDCRAFT, frozen county) and "Endless Night: Minecraft" (repo ENDLESS-NIGHT, Reach world). This project stays for the Unreal 5 version (Cortana R&D stays here).
Handover packs at docs/handover/ on branch claude/project-thread-l4rxug: ENDLESS-NIGHT PR #1, ZOMBOIDCRAFT PR #6 (to main; he must merge for a fresh clone to see them). They contain all 367 memory notes verbatim, RULES.md, ASSETS-METHOD.md, THREADS.md (48 threads), PREVIEWS.md (185 links), DECLINED.md, BUILD-AND-SETUP.md, PC-LAYOUT + PC-LISTING.tsv, library/ text files, LIBRARY-MANIFEST.tsv, CROSSCHECK.md, FIRST-MESSAGE.md. Builder: docs/handover/tools/build_pack.py.
Paste-in messages: /mnt/project-files/handover/*-first-message.md. PC listing: C:\Dev\Zomboidcraft-Desktop\additions\handover\.
**Why:** he said he can't keep re-explaining to new projects. **How to apply:** if memory changes before he creates the new projects, rerun build_pack.py and push to the same branch.
