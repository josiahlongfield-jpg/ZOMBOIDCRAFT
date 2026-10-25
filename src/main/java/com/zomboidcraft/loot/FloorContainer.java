package com.zomboidcraft.loot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ground round your feet as a container: each slot is one dropped item entity in reach. Taking from a slot
 * takes from the entity; putting something in an empty slot sets it down just in front of you.
 */
final class FloorContainer implements Container, Nearby.Reach {
    static final int SIZE = 27;
    private static final double REACH = 2.5;

    private final Player player;
    private final ItemEntity[] bound = new ItemEntity[SIZE];
    private final int[] counts = new int[SIZE];
    private int ticks;

    FloorContainer(Player player) {
        this.player = player;
        scan();
    }

    private AABB area() {
        return player.getBoundingBox().inflate(REACH, 1.0, REACH);
    }

    private boolean live(ItemEntity entity) {
        return entity != null && entity.isAlive() && !entity.getItem().isEmpty() && area().intersects(entity.getBoundingBox());
    }

    /** Lets go of items that went or were picked up, and binds new ones in reach to free slots, nearest first. */
    private void scan() {
        for (int i = 0; i < SIZE; i++) {
            if (bound[i] != null && !live(bound[i])) {
                bound[i] = null;
            }
        }
        var found = player.level().getEntitiesOfClass(ItemEntity.class, area(), e -> e.isAlive() && !e.getItem().isEmpty());
        found.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        outer:
        for (ItemEntity entity : found) {
            int free = -1;
            for (int i = 0; i < SIZE; i++) {
                if (bound[i] == entity) {
                    continue outer;
                }
                if (bound[i] == null && free < 0) {
                    free = i;
                }
            }
            if (free < 0) {
                return;
            }
            bound[free] = entity;
            counts[free] = entity.getItem().getCount();
        }
    }

    /** Server tick while the menu is open. */
    void tick() {
        if (++ticks % 10 == 0) {
            scan();
        }
        for (int i = 0; i < SIZE; i++) {
            ItemEntity entity = bound[i];
            if (entity == null) {
                continue;
            }
            if (!entity.isAlive()) {
                bound[i] = null;
                continue;
            }
            ItemStack item = entity.getItem();
            if (item.isEmpty()) {
                entity.discard();
                bound[i] = null;
                continue;
            }
            // a stack changed in place: hand the entity a copy so watching clients see the new count
            if (item.getCount() != counts[i]) {
                counts[i] = item.getCount();
                entity.setItem(item.copy());
            }
            // while you're going through the floor, what's on it stays put instead of jumping into your pockets
            entity.setPickUpDelay(20);
        }
    }

    @Override
    public boolean inReach(Player player) {
        return true;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        ItemEntity entity = bound[slot];
        return entity == null || !entity.isAlive() ? ItemStack.EMPTY : entity.getItem();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemEntity entity = bound[slot];
        if (entity == null || !entity.isAlive()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = entity.getItem().split(count);
        settle(slot);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemEntity entity = bound[slot];
        if (entity == null || !entity.isAlive()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = entity.getItem();
        entity.setItem(ItemStack.EMPTY);
        settle(slot);
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemEntity entity = bound[slot];
        if (entity != null && entity.isAlive()) {
            entity.setItem(stack);
            settle(slot);
        } else if (!stack.isEmpty()) {
            bound[slot] = drop(stack);
            counts[slot] = stack.getCount();
        }
    }

    /** After a change: an emptied entity goes, a changed one is resent. */
    private void settle(int slot) {
        ItemEntity entity = bound[slot];
        ItemStack item = entity.getItem();
        if (item.isEmpty()) {
            entity.discard();
            bound[slot] = null;
        } else {
            counts[slot] = item.getCount();
            entity.setItem(item.copy());
        }
    }

    /** Sets a stack down on the ground a step in front of you, out of pickup range, or at your feet if that's blocked. */
    private ItemEntity drop(ItemStack stack) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        Vec3 ahead = look.lengthSqr() < 1.0e-4 ? Vec3.ZERO : look.normalize().scale(1.6);
        Vec3 at = player.position().add(ahead);
        ItemEntity entity = new ItemEntity(player.level(), at.x, at.y + 0.1, at.z, stack, 0, 0.05, 0);
        if (!player.level().noCollision(entity)) {
            entity.setPos(player.getX(), player.getY() + 0.1, player.getZ());
        }
        entity.setPickUpDelay(40);
        player.level().addFreshEntity(entity);
        return entity;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
    }
}
