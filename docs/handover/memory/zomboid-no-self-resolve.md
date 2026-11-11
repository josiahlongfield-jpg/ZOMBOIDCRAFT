---
name: zomboid-no-self-resolve
description: RULE from Josiah 2026-10-29: threads never resolve themselves unless he authorises it
metadata:
  type: feedback
  modified: 2026-10-29T01:44:58.731Z
---

Josiah 2026-10-29 01:44 (via the coordinator): no thread may resolve itself unless he authorises it.

**Why:** he wants threads to stay visible until he decides they're done.
**How to apply:** after delivering, leave the thread open (never call set_thread_resolved on your own). Resolve only when Josiah says so in that thread or through a cited message of his.
