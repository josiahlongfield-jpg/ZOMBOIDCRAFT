---
name: zomboid-no-self-resolve-rule
description: RULE (Josiah 2026-10-29) - no thread resolves itself unless Josiah authorises it
metadata:
  type: feedback
---

Josiah 2026-10-29 01:44: "tell all threads not to resolve themself unless I authorise it".

**Why:** he wants to decide himself when a piece of work is finished.

**How to apply:** never call set_thread_resolved on your own, even after a delivered result; leave the thread open until Josiah says it can be resolved.
