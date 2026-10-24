#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }

echo "===== compile errors"
grep -E -A3 'error:' build.log | grep -v '^--$' | head -120 || true
