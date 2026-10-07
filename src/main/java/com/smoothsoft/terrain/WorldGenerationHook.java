package com.smoothsoft.terrain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Fluxo (somente durante a geracao do chunk, logo apos o surface builder):
 * Original Heightmap -> analise multi-escala -> Smoothed -> Blended -> remodelagem -> SurfaceValidationPass.
 * Qualquer erro = chunk intacto.
 */
public final class WorldGenerationHook {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    /** Altura planejada da superficie por chunk (do fim do surface ate depois dos carvers). */
    private static final ConcurrentHashMap<Long, short[]> PLANNED = new ConcurrentHashMap<>();

    private static long key(Chunk c) {
        return ((long) (c.getPos().getStartX() >> 4) << 32) ^ ((c.getPos().getStartZ() >> 4) & 0xFFFFFFFFL);
    }

    private WorldGenerationHook() {}

    public static void apply(NoiseChunkGenerator gen, ChunkRegion region, NoiseConfig nc,
                             StructureAccessor sa, Chunk chunk) {
        TerrainConfig cfg = TerrainConfig.get();
        if (!cfg.isActive()) return;
        try {
            if (cfg.only_new_chunks && !Blender.getBlender(region).equals(Blender.getNoBlending())) return;
            if (!allowedGenerator(gen, cfg)) return;

            int sea = gen.getSeaLevel();
            SmoothParams params = cfg.params();
            int margin = cfg.chunk_blending ? params.requiredMargin() : 0;
            ChunkStats stats = new ChunkStats();

            StructureProtection sp = cfg.preserve_structures
                ? StructureProtection.collect(sa, chunk.getPos()) : StructureProtection.NONE;

            // 1) Original heightmap (janela com margem: chunk + >= 36 blocos de cada lado)
            double[][] R = HeightmapSampler.sample(gen, nc, chunk, margin, sea);
            // 2) analise (micro/local/regional/macro, slope, curvatura) + 3) smoothed
            var analysis = TerrainAnalyzer.analyze(R, params);
            int n = R.length;
            double[][] biome = new double[n][n];
            double[][] tscale = new double[n][n];
            for (double[] row : biome) java.util.Arrays.fill(row, 1.0);
            for (double[] row : tscale) java.util.Arrays.fill(row, 1.0);
            java.util.Set<String> noGrass = new java.util.HashSet<>(cfg.no_grass_biomes);
            int minX = chunk.getPos().getStartX(), minZ = chunk.getPos().getStartZ();
            for (int i = 0; i < HeightmapSampler.INNER; i++) {
                for (int j = 0; j < HeightmapSampler.INNER; j++) {
                    int x = minX + i * HeightmapSampler.CELL, z = minZ + j * HeightmapSampler.CELL;
                    int y = (int) R[margin + i][margin + j];
                    var biomeEntry = gen.getBiomeSource().getBiome(x >> 2, y >> 2, z >> 2, nc.getMultiNoiseSampler());
                    biome[margin + i][margin + j] = BiomeProtection.factor(biomeEntry, cfg);
                    // desertos/badlands/praias: dunas e encostas suaves, sem "degraus de grama"
                    if (noGrass.contains(BiomeProtection.id(biomeEntry))) tscale[margin + i][margin + j] = 0.3;
                }
            }
            var fields = TerrainSmoother.smooth(R, analysis, params, biome, tscale,
                minX - margin * HeightmapSampler.CELL, minZ - margin * HeightmapSampler.CELL, sea);

            double[][] raw5 = inner(R, margin), hs5 = inner(fields.hs, margin), st5 = inner(fields.strength, margin),
                tr5 = inner(fields.terrace, margin);
            // 3b) terreno sempre apoiado (fecha vazios finos / fragmentos flutuantes) -- antes do preenchimento de pocos
            SurfaceSupportPass.run(gen, chunk, sp, cfg, sea, stats);
            // 4) blended + final: aplica ao chunk (inclui limite de slope e preenchimento de pocos)
            TerrainRemodeler.apply(gen, chunk, raw5, hs5, st5, tr5, sp, cfg, params, sea, stats);
            // 5) cobertura de grama nas superficies expostas
            if (cfg.surface_grass_pass) SurfaceValidationPass.run(chunk, sp, cfg, sea, stats);

            if (cfg.seal_surface_holes) {
                Heightmap fl = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
                short[] planned = new short[256];
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) planned[lx * 16 + lz] = (short) fl.get(lx, lz);
                if (PLANNED.size() > 8192) PLANNED.clear();
                PLANNED.put(key(chunk), planned);
            }
            if (cfg.debug_visualization) {
                SmoothSoftTerrainMod.LOGGER.info(stats.format(minX >> 4, minZ >> 4, cfg.effectiveStrength()));
            }
        } catch (Throwable t) {
            if (WARNED.compareAndSet(false, true)) {
                SmoothSoftTerrainMod.LOGGER.warn("Smooth/Soft Terrain falhou; chunk mantido vanilla.", t);
            }
        }
    }

    /** Chamado no inicio da etapa de features (logo apos os carvers): fecha buracos pequenos de superficie. */
    public static void applyAfterCarve(NoiseChunkGenerator gen, StructureAccessor sa, Chunk chunk) {
        TerrainConfig cfg = TerrainConfig.get();
        short[] planned = PLANNED.remove(key(chunk));
        if (planned == null || !cfg.isActive() || !cfg.seal_surface_holes) return;
        try {
            StructureProtection sp = cfg.preserve_structures
                ? StructureProtection.collect(sa, chunk.getPos()) : StructureProtection.NONE;
            ChunkStats stats = new ChunkStats();
            HoleSealer.run(gen, chunk, planned, sp, cfg, gen.getSeaLevel(), stats);
            if (cfg.debug_visualization && stats.sealed > 0) {
                SmoothSoftTerrainMod.LOGGER.info("[SmoothSoftTerrain] Chunk {} {}: {} colunas de buraco seladas",
                    chunk.getPos().getStartX() >> 4, chunk.getPos().getStartZ() >> 4, stats.sealed);
            }
        } catch (Throwable t) {
            if (WARNED.compareAndSet(false, true)) {
                SmoothSoftTerrainMod.LOGGER.warn("Smooth/Soft Terrain (selagem) falhou; chunk mantido.", t);
            }
        }
    }

    private static double[][] inner(double[][] f, int m) {
        int N = HeightmapSampler.INNER;
        double[][] o = new double[N][N];
        for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) o[i][j] = f[m + i][m + j];
        return o;
    }

    private static boolean allowedGenerator(NoiseChunkGenerator gen, TerrainConfig cfg) {
        RegistryEntry<ChunkGeneratorSettings> s = gen.getSettings();
        Optional<RegistryKey<ChunkGeneratorSettings>> key = s.getKey();
        return key.isPresent() && cfg.allowed_generator_settings.contains(key.get().getValue().toString());
    }
}
