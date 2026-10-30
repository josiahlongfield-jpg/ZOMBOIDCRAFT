package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.cars.CarAssets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.InflaterInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * The picture or reticle an optic shows on the sights. A sight whose pack came with its own reticle ships it like the
 * bought meshes (sights/<key>.zsight, scrambled; tools/meshguns/reticles.py) and is unpacked into a texture the first
 * time it's looked through; the rest are plain textures (textures/gui/sight/<key>.png).
 */
public final class SightTextures {
    private static final Map<String, Identifier> LOADED = new HashMap<>();

    public static Identifier get(String key) {
        return LOADED.computeIfAbsent(key, SightTextures::load);
    }

    private static Identifier load(String key) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> packed = mc.getResourceManager().getResource(ZomboidCraft.id("sights/" + key + ".zsight"));
        if (packed.isPresent()) {
            try (InputStream in = packed.get().open()) {
                byte[] data = in.readAllBytes();
                CarAssets.unscramble(data, "sight/" + key);
                byte[] png;
                try (InputStream inflated = new InflaterInputStream(new ByteArrayInputStream(data))) {
                    png = inflated.readAllBytes();
                }
                Identifier id = ZomboidCraft.id("dynamic/sight/" + key);
                mc.getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft sight " + key,
                        NativeImage.read(new ByteArrayInputStream(png))));
                return id;
            } catch (IOException e) {
                ZomboidCraft.LOGGER.warn("Couldn't read the {} sight", key, e);
            }
        }
        return ZomboidCraft.id("textures/gui/sight/" + key + ".png");
    }

    private SightTextures() {
    }
}
