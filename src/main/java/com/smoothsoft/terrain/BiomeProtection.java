package com.smoothsoft.terrain;

import java.util.Optional;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

/** Nunca troca biomas; apenas reduz a intensidade onde o relevo e a identidade do bioma. */
final class BiomeProtection {
    private BiomeProtection() {}

    static double factor(RegistryEntry<Biome> entry, TerrainConfig cfg) {
        if (!cfg.preserve_biomes) return 1.0;
        Optional<RegistryKey<Biome>> key = entry.getKey();
        if (key.isEmpty()) return TerrainMath.clamp01(cfg.modded_biome_factor);
        Identifier id = key.get().getValue();
        Double f = cfg.biome_factors.get(id.toString());
        if (f != null) return TerrainMath.clamp01(f);
        if (!"minecraft".equals(id.getNamespace())) return TerrainMath.clamp01(cfg.modded_biome_factor);
        return 1.0;
    }

    static String id(RegistryEntry<Biome> entry) {
        return entry.getKey().map(k -> k.getValue().toString()).orElse("");
    }
}
