#!/usr/bin/env bash
# Refits the walkers' hit capsules to the drawn body and rewrites src/main/java/com/zomboidcraft/combat/BodyCaps.java.
#   tools/zombies/hitcaps/run.sh [POSE...]     (no poses: all of them; takes about 15 minutes on 4 cores)
# Rerun it whenever the zombie body (zbody/*.bin) or its movements (ZMove) change.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HERE="$ROOT/tools/zombies/hitcaps"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" \
    "$HERE"/*.java "$HERE"/stub/com/zomboidcraft/ZomboidCraft.java "$HERE"/stub/com/zomboidcraft/client/zombie/body/ZDetail.java \
    "$ROOT/src/client/java/com/zomboidcraft/client/zombie/body/ZMove.java" \
    "$ROOT/src/client/java/com/zomboidcraft/client/zombie/body/ZBreak.java" \
    "$ROOT/src/main/java/com/zomboidcraft/zombie/ClimbPath.java" \
    "$ROOT/src/client/java/com/zomboidcraft/client/zombie/body/ZBodyMesh.java" \
    "$ROOT/src/main/java/com/zomboidcraft/combat/BodyCaps.java"
java -Xmx10g -cp "$OUT:$ROOT/src/main/resources" FitAll "$ROOT/src/main/java/com/zomboidcraft/combat/BodyCaps.java" "$@"
