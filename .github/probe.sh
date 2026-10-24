#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }


echo "===== stage 3 rendering"
classes 'client/renderer/entity/(Zombie|AbstractZombie|HumanoidMob|LivingEntity|Mob)Renderer|client/renderer/entity/state/(Zombie|Humanoid|LivingEntity)RenderState|client/model/(Zombie|AbstractZombie|Humanoid)Model|client/model/geom/ModelLayers|EntityRendererRegistry|EntityRendererProvider|AnimationUtils' 
sig net.minecraft.client.renderer.entity.ZombieRenderer '.'
sig net.minecraft.client.renderer.entity.AbstractZombieRenderer '.'
sig net.minecraft.client.renderer.entity.HumanoidMobRenderer '.'
sig net.minecraft.client.renderer.entity.LivingEntityRenderer 'public|protected'
sig net.minecraft.client.renderer.entity.state.ZombieRenderState '.'
sig net.minecraft.client.renderer.entity.state.HumanoidRenderState '.'
sig net.minecraft.client.renderer.entity.state.LivingEntityRenderState '.'
sig net.minecraft.client.model.ZombieModel '.'
sig net.minecraft.client.model.AbstractZombieModel '.'
sig net.minecraft.client.model.HumanoidModel 'public|protected'
sig net.minecraft.client.model.AnimationUtils '.'
sig net.minecraft.client.model.geom.ModelLayers 'ZOMBIE|HUSK'
sig net.minecraft.client.model.geom.ModelPart 'public float|public void|public boolean'
sig net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry '.'
sig net.minecraft.client.renderer.entity.EntityRendererProvider\$Context 'public'
sig net.minecraft.client.renderer.entity.EntityRenderer 'public|protected'
echo "===== stage 3 model layers"
classes 'EntityModelLayerRegistry|ModelLayerRegistry|client/model/geom/(PartPose|ModelLayerLocation)|client/model/geom/builders/(LayerDefinition|MeshDefinition|PartDefinition|CubeListBuilder|CubeDeformation)'
sig net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry '.'
sig net.minecraft.client.model.geom.ModelLayerLocation 'public'
sig net.minecraft.client.model.geom.PartPose 'public static'
sig net.minecraft.client.model.geom.builders.LayerDefinition 'public static'
sig net.minecraft.client.model.geom.builders.PartDefinition 'public'
sig net.minecraft.client.model.geom.builders.CubeListBuilder 'texOffs|addBox|create'
sig net.minecraft.client.model.geom.ModelPart 'getChild'
echo "===== stage 3 AI"
sig net.minecraft.world.entity.ai.goal.GoalSelector 'public'
sig net.minecraft.world.entity.ai.goal.WrappedGoal 'public'
sig net.minecraft.world.entity.ai.goal.target.TargetGoal 'public|protected'
sig net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal 'public|protected'
sig net.minecraft.world.entity.Mob 'targetSelector|getSensing|hasLineOfSight|setTarget|getTarget'
sig net.minecraft.world.entity.ai.sensing.Sensing 'public'
sig net.minecraft.world.entity.LivingEntity 'getVoicePitch|yHeadRot|hasLineOfSight|getViewVector'
sig net.minecraft.world.entity.Entity 'getViewVector|getEyePosition|isDiscrete|isCrouching|isShiftKeyDown|getLookAngle'
sig net.minecraft.world.level.Level 'getMaxLocalRawBrightness|getRawBrightness|getBrightness'
sig net.minecraft.world.level.LevelReader 'getMaxLocalRawBrightness|getRawBrightness'
sig net.minecraft.world.level.BlockAndTintGetter 'getRawBrightness|getBrightness'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate '.'

echo "===== compile errors"
grep -E -A3 'error:' build.log | grep -v '^--$' | head -120 || true
