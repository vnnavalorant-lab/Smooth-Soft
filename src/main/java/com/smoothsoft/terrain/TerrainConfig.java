package com.smoothsoft.terrain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** config/smoothsoftterrain.json. Campos publicos em snake_case para o Gson. */
public final class TerrainConfig {
    private static final int CONFIG_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile TerrainConfig instance = new TerrainConfig();

    public int config_version = CONFIG_VERSION;
    public boolean enabled = true;
    /** OFF | SUBTLE | NATURAL | STRONG_NATURAL | VERY_SMOOTH | SOFT_WORLD (padrao) | CUSTOM.
     *  strength/radius/regional_radius/macro_radius/detail_keep/max_slope so valem em CUSTOM. */
    public String mode = "SOFT_WORLD";
    public double strength = 0.65;
    public int radius = 12;
    public int regional_radius = 24;
    public int macro_radius = 40;
    public double detail_keep = 0.30;
    /** Inclinacao maxima desejada (blocos de altura por bloco horizontal). */
    public double max_slope = 0.70;
    /** Preenche pocos/buracos de superficie (depressoes rasas e isoladas). */
    public boolean fill_pits = true;
    /** Espessura minima de terreno solido sob a superficie (fecha vazios finos / fragmentos flutuantes). */
    public int min_surface_support = 5;
    /** Apos as cavernas: fecha aberturas pequenas na superficie; aberturas grandes (cavernas/ravinas) ficam. */
    public boolean seal_surface_holes = true;
    public int max_sealed_hole_area = 36;

    public boolean preserve_biomes = true;
    public boolean preserve_structures = true;
    public boolean preserve_caves = true;
    public boolean preserve_water = true;
    public boolean chunk_blending = true;
    public boolean only_new_chunks = true;
    /** Limite duro de blocos que uma coluna pode subir/descer. */
    public int max_height_change = 24;
    /** Fator (0..1) para biomas de outros mods sem entrada em biome_factors. */
    public double modded_biome_factor = 0.85;
    public Map<String, Double> biome_factors = defaultBiomeFactors();
    /** Somente estes ChunkGeneratorSettings sao tocados (dimensoes/mundos custom ficam intactos). */
    public List<String> allowed_generator_settings = new ArrayList<>(
        List.of("minecraft:overworld", "minecraft:large_biomes"));

    /** Etapa final: DIRT natural exposto ao ceu/ar vira GRASS_BLOCK. */
    public boolean surface_grass_pass = true;
    public boolean convert_coarse_dirt = false;
    public List<String> no_grass_biomes = new ArrayList<>(List.of(
        "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
        "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "minecraft:mangrove_swamp"));

    public boolean debug_visualization = false;

    private static Map<String, Double> defaultBiomeFactors() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("minecraft:jagged_peaks", 0.60);
        m.put("minecraft:frozen_peaks", 0.65);
        m.put("minecraft:stony_peaks", 0.65);
        m.put("minecraft:eroded_badlands", 0.35);
        m.put("minecraft:badlands", 0.70);
        m.put("minecraft:wooded_badlands", 0.70);
        m.put("minecraft:stony_shore", 0.60);
        return m;
    }

    public static TerrainConfig get() { return instance; }

    private String modeUpper() { return mode == null ? "SOFT_WORLD" : mode.toUpperCase(Locale.ROOT); }

    public double effectiveStrength() {
        return switch (modeUpper()) {
            case "OFF" -> 0.0;
            case "SUBTLE" -> 0.30;
            case "NATURAL" -> 0.45;
            case "STRONG_NATURAL" -> 0.65;
            case "VERY_SMOOTH" -> 0.85;
            case "CUSTOM" -> TerrainMath.clamp(strength, 0.0, 1.0);
            default -> 0.90; // SOFT_WORLD
        };
    }

    public SmoothParams params() {
        return switch (modeUpper()) {
            case "SUBTLE" -> SmoothParams.of(0.30, 8, 16, 28, 0.45, 1.4);
            case "NATURAL" -> SmoothParams.of(0.45, 10, 20, 32, 0.38, 1.1);
            case "STRONG_NATURAL" -> SmoothParams.of(0.65, 12, 24, 40, 0.30, 0.9);
            case "VERY_SMOOTH" -> SmoothParams.of(0.85, 14, 28, 44, 0.22, 0.7);
            case "CUSTOM" -> SmoothParams.of(effectiveStrength(), radius, regional_radius, macro_radius,
                TerrainMath.clamp01(detail_keep), max_slope);
            default -> SmoothParams.of(0.90, 16, 32, 46, 0.12, 0.7); // SOFT_WORLD
        };
    }

    public boolean isActive() { return enabled && effectiveStrength() > 0.0; }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("smoothsoftterrain.json");
        TerrainConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                JsonObject o = GSON.fromJson(r, JsonObject.class);
                int v = (o != null && o.has("config_version")) ? o.get("config_version").getAsInt() : 1;
                if (v >= CONFIG_VERSION) {
                    cfg = GSON.fromJson(o, TerrainConfig.class);
                } else {
                    // config antiga (muito conservadora): substituida pelos novos padroes
                    SmoothSoftTerrainMod.LOGGER.info("Config antiga (v{}) substituida pelos novos padroes (v{}).",
                        v, CONFIG_VERSION);
                }
            } catch (Exception e) {
                SmoothSoftTerrainMod.LOGGER.warn("Config invalida, usando padrao: {}", e.toString());
            }
        }
        if (cfg == null) cfg = new TerrainConfig();
        cfg.config_version = CONFIG_VERSION;
        if (cfg.mode == null) cfg.mode = "SOFT_WORLD";
        if (cfg.allowed_generator_settings == null) cfg.allowed_generator_settings = new ArrayList<>();
        if (cfg.biome_factors == null) cfg.biome_factors = new LinkedHashMap<>();
        if (cfg.no_grass_biomes == null) cfg.no_grass_biomes = new ArrayList<>();
        instance = cfg;
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(cfg, w);
        } catch (IOException e) {
            SmoothSoftTerrainMod.LOGGER.warn("Nao foi possivel salvar a config: {}", e.toString());
        }
    }
}
