#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }

echo "===== round 2"
classes 'fabric/api/.*([Ii]tem[Gg]roup|[Cc]reative|Tab)[^$]*\.class$'
classes 'server/permissions/[^$]*\.class$'
sig net.minecraft.server.permissions.PermissionSet '.'
sig net.minecraft.commands.Commands 'ermission|LEVEL'
sig net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement '.'
sig net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry 'add'
sig net.minecraft.world.entity.monster.zombie.Zombie 'Sun|registerGoals|BreakDoors|Baby'
sig net.minecraft.world.entity.Mob 'Sun|burn'
