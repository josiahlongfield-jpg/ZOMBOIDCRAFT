package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The outfits' raised 3D pieces (caps, helmets, pouches, collars, hoods, the hanging jaw), drawn
 * as a second pass over the walker. It is the walker's own skeleton and animation with the body
 * boxes skipped, so every piece follows its bone exactly; only the current outfit's pieces show.
 */
public class WalkerAddonModel extends WalkerModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ZomboidCraft.id("walker"), "addons");

    private final Map<String, List<ModelPart>> pieces = new HashMap<>();
    private final List<ModelPart> rightArmPieces = new ArrayList<>();
    private final List<ModelPart> leftArmPieces = new ArrayList<>();
    private final List<ModelPart> gear;

    public WalkerAddonModel(ModelPart root) {
        super(root);
        Map<String, ModelPart> bones = Map.of("head", head, "body", body, "right_arm", rightArm,
                "left_arm", leftArm, "right_leg", rightLeg, "left_leg", leftLeg);
        WalkerAddonParts.PIECES.forEach((outfit, names) -> {
            List<ModelPart> parts = new ArrayList<>();
            for (String name : names) {
                String bone = WalkerAddonParts.BONE.get(name);
                ModelPart part = bones.get(bone).getChild(name);
                parts.add(part);
                if (bone.equals("right_arm")) {
                    rightArmPieces.add(part);
                } else if (bone.equals("left_arm")) {
                    leftArmPieces.add(part);
                }
            }
            pieces.put(outfit, parts);
        });
        gear = List.of(head.getChild("visor"), head.getChild("brim"), body.getChild("pack"),
                rightArm.getChild("stub"), rightArm.getChild("bone"), leftArm.getChild("stub"), leftArm.getChild("bone"));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = WalkerModel.createMesh();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> bones = new HashMap<>();
        for (String bone : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            bones.put(bone, root.getChild(bone));
        }
        WalkerAddonParts.define(bones);
        return LayerDefinition.create(mesh, WalkerAddonParts.TEX_SIZE, WalkerAddonParts.TEX_SIZE);
    }

    @Override
    public void setupAnim(WalkerRenderState state) {
        super.setupAnim(state);
        for (ModelPart part : List.of(head, body, rightArm, leftArm, rightLeg, leftLeg)) {
            part.skipDraw = true;
        }
        hat.visible = false;
        for (ModelPart part : gear) {
            part.visible = false;
        }
        pieces.forEach((outfit, parts) -> {
            boolean show = outfit.equals(state.outfit);
            for (ModelPart part : parts) {
                part.visible = show;
            }
        });
        // A torn-off forearm takes its sleeve pieces with it; a missing arm hides its bone already.
        if (state.missingLimb == 1) {
            rightArmPieces.forEach(part -> part.visible = false);
        } else if (state.missingLimb == 2) {
            leftArmPieces.forEach(part -> part.visible = false);
        }
    }
}
