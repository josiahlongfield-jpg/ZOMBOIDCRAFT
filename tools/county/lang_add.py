#!/usr/bin/env python3
"""Adds or updates lang keys in assets/zomboidcraft/lang/en_us.json without reordering the rest.

    python3 tools/county/lang_add.py key=value [key=value ...]
"""
import json
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parents[2] / "src/main/resources/assets/zomboidcraft/lang/en_us.json"
lang = json.loads(PATH.read_text())
for arg in sys.argv[1:]:
    k, v = arg.split("=", 1)
    lang[k] = v
PATH.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
