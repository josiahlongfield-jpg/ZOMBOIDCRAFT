---
name: zomboid-no-wikimedia-rule
description: RULE (Josiah 2026-10-31, clarified 04:52) photo refs are fetched only through his PC, never from the cloud container
metadata:
  type: feedback
---

Josiah 2026-10-31 04:39: "Forget about ever using Wikipedia it's terrible and a waste of usage. All photo references will come from my pc when that's available."
Clarified 04:52 in REFERENCES: "You use my pc to search them as you have been. I'm just choosing to avoid the Wikimedia option that caused you to have a single photo every 20-40min".

**Why:** the slow option was the cloud container, which Wikimedia throttles to about one photo every 20-40 min. The PC runs (pc_refs.py over Remote Control) are fast and he's happy with them. They also search Wikimedia Commons; REFERENCES told him that on 2026-10-31.

Final wording 04:53: "Then restrict wikimedia to only be used from my pc".

**How to apply:** never fetch photos from the cloud container (batch.py, curl). Run pc_refs.py on his PC through Remote Control, and only once he says the PC is on. Drop off-topic hits before merging. The photo-first rule in [[zomboid-photo-reference-rule]] still stands, and the method is in [[zomboid-refs-collection]].
