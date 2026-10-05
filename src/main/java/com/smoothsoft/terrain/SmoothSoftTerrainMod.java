package com.smoothsoft.terrain;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmoothSoftTerrainMod implements ModInitializer {
    public static final String MOD_ID = "smoothsoftterrain";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TerrainConfig.load();
        TerrainConfig c = TerrainConfig.get();
        SmoothParams p = c.params();
        LOGGER.info("[SmoothSoftTerrain] enabled={} mode={} strength={} raios(blocos): micro={} local={} regional={} macro={} margem={} blocos",
            c.enabled, c.mode, c.effectiveStrength(), p.microR(), p.localR(), p.regionalR(), p.macroR(),
            p.requiredMargin() * 4);
    }
}
