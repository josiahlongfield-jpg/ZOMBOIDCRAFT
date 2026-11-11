---
name: zomboid-thread-resolve-rule
description: RULE (Josiah 2026-10-29) - no thread resolves itself unless he authorises it
metadata:
  type: feedback
---

Josiah 2026-10-29 01:44: threads must never resolve themselves unless he authorises it.

**Why:** he wants to decide when work is finished, not have threads vanish from his list.
**How to apply:** after delivering a result, leave the thread open; call set_thread_resolved only after he explicitly says to resolve/close it.
