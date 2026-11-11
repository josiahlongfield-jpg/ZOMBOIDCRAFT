---
name: zomboid-pc-storage-rule
description: Josiah 2026-11-08 00:09 STRICT: never save or back up anything to OneDrive folders on his PC; use C:\Dev\Zomboidcraft-Desktop\additions instead
metadata:
  type: feedback
---
RULE (Josiah 2026-11-08 00:09, to all threads): do not save anything or back up to OneDrive folders. Every file we put on his PC (raw Tarkov bundle copies, zips, scripts, archives, backups) goes under C:\Dev\Zomboidcraft-Desktop\additions.
The game profile itself (C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)\config\zomboidcraft\...) is not OneDrive and stays where the game reads it.
**Why:** the old additions folder (C:\Attachments\OneDrive\Desktop\additions) is OneDrive-synced; files vanished from it before (first Tarkov raw copy), and it eats his cloud storage.
**How to apply:** Remote Control copy scripts write to C:\Dev\Zomboidcraft-Desktop\additions\tarkov-pilot\raw etc.; request folder access to C:\Dev\Zomboidcraft-Desktop\additions (not the OneDrive one) when staging. Old paths in [[tarkov-assets-pipeline-tech]], [[tarkov-weapon-batch3]] and the Brita archive are history only.
