# Jar, CI, playtest flow and the PC setup

## One-jar rule (Josiah 2026-10-25, STRICT)
"is there a way every new jar im given can have all updated stuff in it so i dont have to have multiple".
- Work on your own branch off `main`. When Josiah should try something, merge it into `playtest`
  (`git push origin HEAD:refs/heads/playtest`), wait for GREEN CI, then send him the ONE fixed link (never a per-branch jar).
- CI publishes the prerelease tag `playtest-build`. The tag must never share the branch name.
- Check before sending: release `draft` is false and `target_commitish` is your merge SHA (a run once left a draft → 404).
  A newer playtest push cancels an older run: check your merge is an ancestor of the published commit.
- CI stamps the commit into the mod version (`0.5.2+abc1234`), visible in the Modrinth mod list and `logs/latest.log`.
  When he says something is missing, compare his version with the release's `target_commitish` first.
- After `git merge origin/playtest` run `git diff --name-only --diff-filter=U` and STOP if anything is listed.
- `build.gradle` sets zip64 on every Zip task (jar passed 65,535 entries). Never drop it in a conflict.
- Cloud can't reach Fabric/Mojang: build through GitHub Actions; read logs with GitHub MCP `get_job_logs`;
  on failure CI runs `.github/probe.sh` (javap of real 26.3 classes). Versions: loader 0.20.1, loom 1.19.x (Gradle 9.9.0
  wrapper since 2026-11-08), fabric-api 0.165.0+26.3, Java 25.
- Full notes (verbatim): `memory/zomboid-playtest-build.md`, `memory/zomboid-mod-build-notes.md`.

## Endless Night: Minecraft
- Repo `josiahlongfield-jpg/ENDLESS-NIGHT` (PRIVATE; fresh history from 2026-11-09). Default branch on GitHub is
  still `staging` (an old stub, force-push blocked): always work from `main` and `playtest`.
- Jar: https://github.com/josiahlongfield-jpg/ENDLESS-NIGHT/releases/download/playtest-build/endless-night-playtest.jar
  (works only when signed in to GitHub; CI checks it via an authenticated `gh release download`).
- Mod id `endlessnight`, packages `com.endlessnight.*`, config `config/endlessnight/`, commands `/en` and `/enhud`.
- Modrinth profile: `C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\ENDLESS NIGHT` (fresh Fabric 26.3, Josiah
  installed the mods himself). Jar backups go to `C:\Dev\Zomboidcraft-Desktop\additions\jar-backups\`.
  Installing a jar for him: list jars + SHA256, never kill a running game, download, check SHA256, move old jar to
  jar-backups, copy new one in (see THREADS.md, LANDSCAPE install steps).
- Game needs ~12 GB memory allocated.

## ZomboidCraft (county world, FROZEN)
- Repo `josiahlongfield-jpg/ZOMBOIDCRAFT`. **The real code is on `playtest`** (`main` is far behind: 2,231 commits).
  Last playtest merge 2026-11-09 07:01 (163e488b8). Frozen since 2026-11-09 07:51: no new merges unless Josiah reopens it.
- Jar: https://github.com/josiahlongfield-jpg/ZOMBOIDCRAFT/releases/download/playtest-build/endless-night-playtest.jar
  (renamed from zomboidcraft-playtest.jar on 2026-11-08).
- Mod id `zomboidcraft`, commands `/zc`, `/zchud`, config `config/zomboidcraft/`.
- Modrinth profile folder `C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)` (shown in the app as
  "ZOMBOIDCRAFT"). Do not write to it unless Josiah asks.

## His PC and mod setup
- Minecraft Java 26.3 via the Modrinth app. Fabric + Sodium + Iris + Photon + Particle Rain + Continuity; e4mc + Simple Voice Chat
  (+ Voice Bridge for e4mc for friends). Lithium, FerriteCore, Chunky recommended; test ImmediatelyFast one at a time.
- Photon settings: never change the Photon Profile; restore sheet `library/zomboid-mod/perf/photon-custom-settings.md`;
  shadows 1536 / 6 chunks, Max Lights ~350, Colored Lights ON, Bloomy Fog OFF. Render distance 8 (RTX 4070 12 GB edge).
  (memory photon-shader-settings.md, zomboid-photon-lpv-haze.md, zomboid-render-distance-limit.md)
- Haze fix: Rainmeter Glass skin blur over the windowed game (F2 clean, F11 fixes).
- Hardware: RTX 4070 12 GB, Ryzen 5700X, ASUS B550M (SVM on), 32 GB RAM, one 2 TB drive (C:), ~14-26 GB free on 2026-11-11
  (a spare M.2 slot exists). WSL2 Ubuntu 24.04 installed 2026-11-11 for Cortana research.
- Friend install page: https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E (txt copy in library/zomboid-mod/friend-install/).
