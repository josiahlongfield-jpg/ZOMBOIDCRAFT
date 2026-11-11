---
name: zomboid-photo-reference-rule
description: RULE - every ZomboidCraft build (houses, churches, factories, bunkers, offices, skyscrapers, ships) starts from real photo references, at maximum detail
metadata:
  type: feedback
  modified: 2026-10-26T08:56:38.933Z
---

Josiah 2026-10-26 08:56 (landscape thread): "everything needs maximum detail so i want you to make sure whenever youre building something that you reference real images so you know what direction to take, that goes for everything from house to churches to factories to bunkers to office buildings, skyscrapers etc".

**Why:** after visiting the carrier and cargo ship he found the interiors far too sparse ("the bones" were fine). He wants believable, dense, real-world-accurate builds everywhere.

**How to apply:**
- Before building or rebuilding any structure, look at real photos of that kind of place (outside AND inside) and list what you see: layout, fittings, clutter, lighting, materials. Build from that list, not from memory.
- Download a few reference photos and view them with Read (images); note the sources in the plan or previews.
- The cloud container blocks most image hosts by default. On 2026-10-26 Josiah was asked to set Network access to Full or allow commons.wikimedia.org, upload.wikimedia.org, media.defense.gov, www.dvidshub.net, www.flickr.com, live.staticflickr.com (Project settings, Cloud environment, gear). Check with `curl -sS "$HTTPS_PROXY/__agentproxy/status"` or a test curl; if still blocked, say so and use text research meanwhile, never pretend to have seen photos.
- Wikimedia Commons API: https://commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=6&srsearch=... then imageinfo&iiprop=url&iiurlwidth=1024 for a thumbnail URL on upload.wikimedia.org.

- 09:10 Josiah: "request images of everything you need to make... if there are things you need to redo id rather we did that now" and "get as many image references as you can to make this world feel real and dense". So EVERY existing place type gets checked against photos and redone where it's off.
- His network change did not reach the already-running landscape container (still 403 at 09:09), so a fresh research thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpRKVvKgiZyL2HSSQT5DQvSH) downloads photos into /mnt/project-files/zomboid-mod/refs/<ships|town|houses|city|farm|military|port|landscape>/<topic>/ with sources.md per folder. Look there first before building anything.

Related: [[zomboid-building-detail-direction]], [[josiah-world-art-direction]], [[zomboid-carrier-direction]].
- Josiah 2026-10-27 22:10 (all threads): ANY creation in the world (structures, items, clothing, guns, gear) pulls images from the REFERENCES thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpRKVvKgiZyL2HSSQT5DQvSH, library /mnt/project-files/zomboid-mod/refs/); ask it for missing photos.
