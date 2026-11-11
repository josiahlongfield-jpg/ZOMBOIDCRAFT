---
name: zomboid-npc-voice-tech
description: Companion voices (2026-10-31): lines.txt moments, fish.audio voice.py, clips from config folder, jaw by loudness, face_dump.py, /zc npc lines|say
metadata:
  type: project
---

Built 2026-10-31 in the Blender tutorial thread (branch claude/project-thread-488ycs). Josiah wants to do it all himself: run sheet Claude Doc "Voice run sheet" https://claude.ai/artifact/3SyLpN6jAMNdmofNvTbPzf (example character `nova`).

- Files per character (local only, never in repo/jar): config/zomboidcraft/npc/MODEL/lines.txt (he writes it), MODEL/voice/CLIP.ogg + index.txt + originals/; API key npc/fish_key.txt.
- Server NpcMoments: parses lines.txt (settings above first [HEADING]; when:/wait:/chance:; lines with fish.audio "(tone)" tags stripped for subtitles). Re-read on file change (2 s). A character WITH lines.txt says only its lines: built-in moments = NpcLines.Kind names (fire at the old call sites; when: narrows), own moments fire on event words (kinds + she_kills/you_kill/you_hurt/you_down/sees_corpse) or, without one, when situations/numbers all hold (checked each second, default wait 60 s). Code-given words (reply, work, alarm...) with no file lines still show unvoiced.
- Clip name = moment + "_" + sha1(raw line)[:8]; voice.py (tools/npc/voice) uses the same rule. voice.py: voice missing via fish.audio POST /v1/tts (format wav, reference_id, model header s1), --check, --convert (mp3/wav/flac -> mono trimmed ogg), --redo.
- Client NpcVoice: NpcNet.Voice payload; plays file through the sound engine as zomboidcraft:npcvoice/MODEL/CLIP/MTIME, loaded by SoundBufferLibraryMixin (getCompleteBuffer HEAD, JOrbisAudioStream); VOICE slider, LINEAR 24 blocks; jaw = loudness envelope 30/s (NpcSkinAnimator Input.voice); subtitles look.npcSubtitles.
- Jaw for his own characters: tools/npc/skinned/face_dump.py run in Blender on the Mixamo upload pose writes face.json (jaw/lid weights + pivots + mesh boxes); pack.py 5th arg; pack.py now matches by mesh box if Mixamo moved/scaled, and "mesh.material" names.
- Commands /zc npc lines, /zc npc say MOMENT (posted to COMMANDS thread? see thread).
- No cloning real people's voices (library voices or his own only).
