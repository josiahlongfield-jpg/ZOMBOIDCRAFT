package com.zomboidcraft.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.county.Terrain;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The county's own biomes, picked from the plan rather than climate noise: open fields, deep
 * woods, damp river bottoms, built-up land, rivers and lakes. Each carries muted, wet colours.
 */
public final class CountyBiomeSource extends BiomeSource {
    public static final ResourceKey<Biome> FIELDS = key("fields"), WOODS = key("woods"), BOTTOMS = key("bottoms"),
            TOWN = key("town"), RIVER = key("river"), LAKE = key("lake");

    public static final MapCodec<CountyBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveElement(FIELDS), RegistryOps.retrieveElement(WOODS), RegistryOps.retrieveElement(BOTTOMS),
            RegistryOps.retrieveElement(TOWN), RegistryOps.retrieveElement(RIVER), RegistryOps.retrieveElement(LAKE)
    ).apply(i, CountyBiomeSource::new));

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, ZomboidCraft.id(name));
    }

    private final Holder<Biome> fields, woods, bottoms, town, river, lake;

    public CountyBiomeSource(Holder<Biome> fields, Holder<Biome> woods, Holder<Biome> bottoms, Holder<Biome> town,
            Holder<Biome> river, Holder<Biome> lake) {
        this.fields = fields;
        this.woods = woods;
        this.bottoms = bottoms;
        this.town = town;
        this.river = river;
        this.lake = lake;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(fields, woods, bottoms, town, river, lake);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> at((qx << 2) + 2, (qz << 2) + 2);
    }

    /** The biome of a column, at block coordinates. */
    public Holder<Biome> at(int x, int z) {
        County county = CountyChunkGenerator.current();
        if (county == null) {
            return fields;
        }
        Terrain t = county.terrain;
        double h = t.height(x, z);
        if (Terrain.isWater(h)) {
            return t.river(x, z) > 0.5 ? river : lake;
        }
        List<Place> near = county.placesNear(x, z, x, z, 0);
        for (Place p : near) {
            if (p.type != PlaceType.FARM) {
                return p.inBasin(x, z) ? lake : town;
            }
        }
        if (near.isEmpty() && t.forest(x, z) > 0.55) {
            return woods;
        }
        if (t.moisture(x, z) > 0.3 && h < Terrain.WATER + 7) {
            return bottoms;
        }
        return fields;
    }
}
