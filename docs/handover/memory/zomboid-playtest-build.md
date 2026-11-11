---
name: zomboid-playtest-build
description: The one-jar rule for ZomboidCraft test builds - shared playtest branch and its fixed download link
metadata:
  type: project
---

Josiah 2026-10-25: "is there a way every new jar im given can have all updated stuff in it so i dont have to have multiple". He was losing features by swapping between per-thread jars.

- Branch `playtest` merges every thread's branch (main + combat + cars cilddw + sounds wh9qba + inventory j1d6bj, ...).
- Its CI publishes prerelease tag `playtest-build` with a fixed-name jar: https://github.com/josiahlongfield-jpg/ZOMBOIDCRAFT/releases/download/playtest-build/endless-night-playtest.jar (renamed from zomboidcraft-playtest.jar 2026-11-08 with the Endless Night display name)
- The tag must not share the branch name (a tag called `playtest` made git refs ambiguous). Push with `git push origin HEAD:refs/heads/playtest`.

**Why:** one jar in his mods folder, always the newest of everything.
**How to apply:** develop on your own branch; when Josiah should try something, merge it into playtest, wait for green, and send him that link (never a per-branch jar). The sound thread can't push to playtest, so the inventory thread merges its commits on request.

Related: [[zomboid-mod-build-notes]], [[zomboid-mod-status]].

26.3 gotcha found the same day: screens open via Gui.setScreen(Screen) (minecraft.gui), not Minecraft.setScreenAndShow. Window has getWidth/getHeight (framebuffer), getGuiScale/setGuiScale(int), calculateScale(int, boolean).

RULE (Josiah 2026-10-26 07:35, via coordinator): every command a thread adds goes to the COMMANDS thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpHu5WqiqREvzovCcDKo5tGF) with a brief description.

- A playtest run gets CANCELLED when a newer playtest push lands; check the newer run contains your merge (git merge-base --is-ancestor).
- Lesson 2026-10-26: a playtest run once left the playtest-build release as a DRAFT (the jar uploaded but it was never published), so the download link 404'd. After a merge, check that the release's `draft` is false and its `target_commitish` is the merge SHA before sending the link. Re-running that playtest Build run recreates it.
- Since 2026-10-26 11:15 CI stamps the commit on the mod version (0.5.2+abc1234, Stamp build version step in build.yml), so the Modrinth app's mod list shows which playtest jar Josiah has. When he says something is missing, first check his version against the release's target_commitish. Release download_count shows whether he fetched the newest jar.
- Friend install page (2026-10-26): https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E, txt copy at project files zomboid-mod/friend-install/zomboidcraft-friend-install.txt. Joiners need Fabric API + playtest jar + Simple Voice Chat + Voice Bridge for e4mc (not e4mc itself). Photonics had no 26.3 build. Update both if the required mod list changes.

- 2026-10-28: the merged jar passed 65535 entries and CI failed at :jar ("enable zip64"). build.gradle now sets zip64 on every Zip task (commit 8eca6993, in playtest 4d582eae). Do not drop it when resolving build.gradle conflicts.

- Lesson 2026-10-31 (LOOT): after `git merge origin/playtest`, check `git diff --name-only --diff-filter=U` and STOP if anything is listed. A follow-up `git add -A && git commit` happily commits a conflicted file (en_us.json with markers) as the merge and it went to playtest. en_us.json fix: take playtest's copy, then re-run tools/items/loot/gen_java.py.
- 2026-10-31: the server smoke test greps "Rain cover:"; any new solid mod block outside CitySpecs must be added to data/minecraft/tags/block/blocks_motion.json and blocks_motion_no_leaves.json or the Build fails.
