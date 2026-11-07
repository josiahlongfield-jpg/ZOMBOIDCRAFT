package com.zomboidcraft.carry.grid;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Tarkov's placing rule over a container laid out as a {@link GridShape}: an item goes where its whole footprint lies
 * inside one grid and covers nobody else. Each stack sits in the slot of its top left cell.
 */
public final class Grids {
    private Grids() {
    }

    /**
     * Whether {@code stack}, as it lies now, can sit with its top left in {@code slot}. {@code ignore} is a slot whose
     * item doesn't count (the one being moved or swapped out), or -1.
     */
    public static boolean fits(Container container, GridShape shape, int slot, ItemStack stack, int ignore) {
        boolean[] taken = taken(container, shape, ignore);
        return fits(shape, taken, slot, ItemSize.of(stack));
    }

    /** Whether a footprint w x h fits with its top left in {@code slot}, given which cells are taken. */
    public static boolean fits(GridShape shape, boolean[] taken, int slot, int[] size) {
        GridShape.Cell c = shape.cell(slot);
        if (c == null) {
            return false;
        }
        GridShape.Grid g = shape.grids().get(c.grid());
        if (c.x() + size[0] > g.w() || c.y() + size[1] > g.h()) {
            return false;
        }
        for (int dy = 0; dy < size[1]; dy++) {
            for (int dx = 0; dx < size[0]; dx++) {
                if (taken[c.start() + (c.y() + dy) * g.w() + c.x() + dx]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Which cells are covered by the items in the container (leaving out {@code ignore}'s). */
    public static boolean[] taken(Container container, GridShape shape, int ignore) {
        int cells = shape.cells();
        boolean[] taken = new boolean[cells];
        int n = Math.min(cells, container.getContainerSize());
        for (int i = 0; i < n; i++) {
            if (i == ignore) {
                continue;
            }
            ItemStack there = container.getItem(i);
            if (there.isEmpty()) {
                continue;
            }
            cover(shape, taken, i, ItemSize.of(there));
        }
        return taken;
    }

    private static void cover(GridShape shape, boolean[] taken, int slot, int[] size) {
        GridShape.Cell c = shape.cell(slot);
        if (c == null) {
            return;
        }
        GridShape.Grid g = shape.grids().get(c.grid());
        for (int dy = 0; dy < size[1] && c.y() + dy < g.h(); dy++) {
            for (int dx = 0; dx < size[0] && c.x() + dx < g.w(); dx++) {
                taken[c.start() + (c.y() + dy) * g.w() + c.x() + dx] = true;
            }
        }
    }

    /**
     * The slot whose item covers {@code slot}, which is {@code slot} itself for a top left cell; -1 when the cell is
     * free. The screen sends clicks on any covered cell to this one.
     */
    public static int owner(Container container, GridShape shape, int slot) {
        GridShape.Cell target = shape.cell(slot);
        if (target == null) {
            return -1;
        }
        GridShape.Grid g = shape.grids().get(target.grid());
        for (int i = target.start(); i < target.start() + g.cells() && i < container.getContainerSize(); i++) {
            ItemStack there = container.getItem(i);
            if (there.isEmpty()) {
                continue;
            }
            int[] s = ItemSize.of(there);
            int x = (i - target.start()) % g.w(), y = (i - target.start()) / g.w();
            if (target.x() >= x && target.x() < x + s[0] && target.y() >= y && target.y() < y + s[1]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The first slot where the stack fits, trying it as it lies and then turned (turning the stack to match), or -1.
     * Reading order, grid by grid, as Tarkov fills a container.
     */
    public static int firstFit(Container container, GridShape shape, ItemStack stack) {
        boolean[] taken = taken(container, shape, -1);
        int[] size = ItemSize.of(stack);
        for (int turn = 0; turn < 2; turn++) {
            for (int slot = 0; slot < shape.cells(); slot++) {
                if (fits(shape, taken, slot, size)) {
                    if (turn == 1) {
                        ItemSize.turn(stack);
                    }
                    return slot;
                }
            }
            if (size[0] == size[1]) {
                break;
            }
            size = new int[]{size[1], size[0]};
        }
        return -1;
    }
}
