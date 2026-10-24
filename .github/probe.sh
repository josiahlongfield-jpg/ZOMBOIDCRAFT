#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }

echo "===== sunburn"
classes '[Ss]un|[Dd]aylight|[Bb]urn' | grep -v '^net/minecraft/client' | head -40
for c in net.minecraft.world.entity.monster.zombie.Zombie net.minecraft.world.entity.Mob net.minecraft.world.entity.monster.Monster net.minecraft.world.entity.LivingEntity net.minecraft.world.entity.Entity; do
  echo "----- $c (sun/burn methods)"
  javap -cp "$CP" -p "$c" 2>&1 | grep -iE 'sun|daylight|burn|fire' || true
done
echo "----- Zombie bytecode calls mentioning sun/burn/fire"
javap -cp "$CP" -p -c net.minecraft.world.entity.monster.zombie.Zombie 2>&1 | grep -iE '^  [a-z].*\(|sun|daylight|burn|igniteForSeconds|setRemainingFireTicks' | grep -iB1 'sun|daylight|burn|ignite|FireTicks' | head -60
echo "----- Mob bytecode"
javap -cp "$CP" -p -c net.minecraft.world.entity.Mob 2>&1 | grep -iE '^  [a-z].*\(|sun|daylight|burn|igniteForSeconds' | grep -iB1 'sun|daylight|burn|ignite' | head -60

echo "===== compile errors"
grep -E -A3 'error:' build.log | grep -v '^--$' | head -120 || true
