package com.zomboidcraft.client.cars;

import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.world.cars.CarProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The driver's hands on the steering wheel. With nothing in hand both are on it. On the move a handgun
 * leaves the left hand on the wheel, and anything else is put down for both; stopped, whatever's held
 * comes up as usual. The wheel and the hands on it turn with the steering.
 */
public final class CarHands {
    /** Hands on the wheel: none, the left alone, or both. */
    public static final int NONE = 0, LEFT = 1, BOTH = 2;
    /** How far the steering wheel turns for the front wheels' turn. */
    public static final float RATIO = 5f;

    /** Which of this rider's hands are on the wheel. */
    public static int onWheel(Entity rider) {
        if (!(rider instanceof Player player) || !(rider.getVehicle() instanceof CarProp car) || car.seatOf(rider) != 0) {
            return NONE;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return BOTH;
        }
        if (!CarProp.handsOnWheel(rider)) {
            return NONE;
        }
        return CarProp.mayUse(rider, held) && GunType.isGun(held) ? LEFT : BOTH;
    }

    /** Whether the local player's left hand is on the wheel while their right holds a handgun. */
    public static boolean leftOnWheel(Player player) {
        return onWheel(player) == LEFT;
    }

    /** Whether the game's own first-person hand (and what it holds) is left out: both hands are on the wheel. */
    public static boolean hidesFirstPerson() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (onWheel(mc.player) == BOTH || com.zomboidcraft.world.cars.Turret.isGunner(mc.player));
    }

    /** How far the steering wheel is turned, radians, anticlockwise as the driver sees it. */
    public static float wheelTurn(CarProp car, float partialTick) {
        return (car.steerO + (car.steer - car.steerO) * partialTick) * RATIO;
    }

    /** The driver's arms as others see them (and in third person): reaching forward to the wheel, rocking as it turns. */
    public static void pose(PlayerModel model, AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        Entity rider = mc.level == null ? null : mc.level.getEntity(state.id);
        if (rider != null && com.zomboidcraft.world.cars.Turret.isGunner(rider)) {
            // at the mounted gun: standing, both hands forward on its grips
            model.leftLeg.xRot = model.rightLeg.xRot = 0f;
            model.leftLeg.yRot = model.rightLeg.yRot = 0f;
            model.leftLeg.zRot = model.rightLeg.zRot = 0f;
            model.leftArm.xRot = model.rightArm.xRot = -1.35f + model.head.xRot * 0.6f;
            model.leftArm.yRot = 0.18f;
            model.rightArm.yRot = -0.18f;
            model.leftArm.zRot = model.rightArm.zRot = 0f;
            return;
        }
        int hands = rider == null ? NONE : onWheel(rider);
        if (hands == NONE || !(rider.getVehicle() instanceof CarProp car)) {
            return;
        }
        float t = Mth.clamp(car.steer * RATIO / 1.4f, -1f, 1f);
        model.leftArm.xRot = -1.2f - 0.35f * t;
        model.leftArm.yRot = 0.32f;
        model.leftArm.zRot = 0f;
        if (hands == BOTH) {
            model.rightArm.xRot = -1.2f + 0.35f * t;
            model.rightArm.yRot = -0.32f;
            model.rightArm.zRot = 0f;
        }
    }

    private CarHands() {
    }
}
