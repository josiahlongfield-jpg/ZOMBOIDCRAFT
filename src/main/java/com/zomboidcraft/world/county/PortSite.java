package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;

/** Placeholder until the full builder lands: levelled ground and the main cross of lanes. */
final class PortSite extends Site {
    PortSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        ground("minecraft:coarse_dirt");
        for (int x = 0; x < w; x++) {
            out.set(x, 0, p.gateZ(), "minecraft:dirt_path");
        }
        for (int z = 0; z < d; z++) {
            out.set(p.gateX(), 0, z, "minecraft:dirt_path");
        }
        fill(w / 2 + 3, 1, d / 2 + 3, w / 2 + 9, 6, d / 2 + 9, "brick_red");
    }
}
