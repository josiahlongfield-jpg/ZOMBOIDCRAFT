package com.zomboidcraft.carry.grid;

import java.util.List;

/**
 * The layout of a Tarkov container: one or more grids of cells (a backpack's one main compartment, a rig's row of
 * pouches). Its slots are the cells, grid after grid, row by row; an item sits in the slot of its top left cell and
 * covers as many cells as its {@link ItemSize} (Josiah 2026-11-07: copy Tarkov's inventory as it is).
 */
public record GridShape(List<Grid> grids) {
    public record Grid(int w, int h) {
        public int cells() {
            return w * h;
        }
    }

    /** Where one slot is: its grid, that grid's first slot, and the cell inside it. */
    public record Cell(int grid, int start, int x, int y) {
    }

    /** A plain block of cells, {@code columns} wide, for a container that has no Tarkov layout of its own. */
    public static GridShape block(int columns, int slots) {
        int w = Math.max(1, columns);
        return new GridShape(List.of(new Grid(w, Math.max(1, (slots + w - 1) / w))));
    }

    /** Tarkov's own layout, as (w, h) pairs in order: {@code of(1,2, 1,2, 2,2)}. */
    public static GridShape of(int... wh) {
        Grid[] grids = new Grid[wh.length / 2];
        for (int i = 0; i < grids.length; i++) {
            grids[i] = new Grid(wh[i * 2], wh[i * 2 + 1]);
        }
        return new GridShape(List.of(grids));
    }

    public int cells() {
        int n = 0;
        for (Grid g : grids) {
            n += g.cells();
        }
        return n;
    }

    /** The cell of a slot, or null past the last cell. */
    public Cell cell(int slot) {
        int start = 0;
        for (int i = 0; i < grids.size(); i++) {
            Grid g = grids.get(i);
            if (slot < start + g.cells()) {
                int local = slot - start;
                return new Cell(i, start, local % g.w(), local / g.w());
            }
            start += g.cells();
        }
        return null;
    }

    /** The slot of a cell of one grid. */
    public int slot(int grid, int x, int y) {
        int start = 0;
        for (int i = 0; i < grid; i++) {
            start += grids.get(i).cells();
        }
        return start + y * grids.get(grid).w() + x;
    }
}
