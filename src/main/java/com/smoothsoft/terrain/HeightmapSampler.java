package com.smoothsoft.terrain;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Amostra a altura "vanilla" de qualquer coluna chamando o proprio gerador
 * (funcao pura de coordenadas + seed: nao depende de chunks vizinhos existirem).
 * Grade global a cada 4 blocos + cache por NoiseConfig: cada no e calculado ~1 vez por mundo.
 */
final class HeightmapSampler {
    static final int CELL = 4;
    static final int INNER = 5; // 4 celulas = 16 blocos -> 5 nos
    private static final int MAX_CACHE = 600_000;
    private static final Map<NoiseConfig, ConcurrentHashMap<Long, Integer>> CACHES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private HeightmapSampler() {}

    /** Matriz (INNER + 2*margin)^2 de alturas (primeiro Y livre acima do piso), minimo = nivel do mar. */
    static double[][] sample(NoiseChunkGenerator gen, NoiseConfig nc, Chunk chunk, int margin, int seaLevel) {
        ConcurrentHashMap<Long, Integer> cache = CACHES.computeIfAbsent(nc, k -> new ConcurrentHashMap<>());
        if (cache.size() > MAX_CACHE) cache.clear();
        int n = INNER + 2 * margin;
        int gx0 = (chunk.getPos().getStartX() >> 4) * 4 - margin;
        int gz0 = (chunk.getPos().getStartZ() >> 4) * 4 - margin;
        double[][] h = new double[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                int gx = gx0 + a, gz = gz0 + b;
                long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
                Integer v = cache.get(key);
                if (v == null) {
                    v = gen.getHeight(gx * CELL, gz * CELL, Heightmap.Type.OCEAN_FLOOR_WG, chunk, nc);
                    cache.put(key, v);
                }
                h[a][b] = Math.max(v, seaLevel); // oceano/rio vira "plano" no nivel do mar na analise
            }
        }
        return h;
    }
}
