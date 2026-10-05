package com.smoothsoft.terrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/**
 * Roda DEPOIS dos carvers. Compara a altura planejada da superficie (guardada pelo hook) com a
 * altura atual: colunas que um carver/ruido rebaixou >= 2 blocos formam "buracos". Cada buraco
 * (componente conexo, 4-vizinhos, dentro do chunk) pequeno (<= max_sealed_hole_area colunas) e que
 * nao encosta na borda do chunk e fechado com terreno + a cobertura do vizinho. Buracos grandes
 * (bocas de caverna, ravinas) e os que cruzam a borda do chunk ficam como estao: sao as excecoes
 * geologicas intencionais.
 */
final class HoleSealer {
    private HoleSealer() {}

    static void run(NoiseChunkGenerator gen, Chunk chunk, short[] planned, StructureProtection sp,
                    TerrainConfig cfg, int sea, ChunkStats stats) {
        BlockState fill = gen.getSettings().value().defaultBlock();
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surf = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        int minX = chunk.getPos().getStartX(), minZ = chunk.getPos().getStartZ();
        int maxDelta = TerrainMath.clampInt(cfg.max_height_change, 1, 48);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        boolean[] hole = new boolean[256];
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int p = planned[lx * 16 + lz];
                hole[lx * 16 + lz] = p > sea + 2 && p - floor.get(lx, lz) >= 2;
            }

        boolean[] seen = new boolean[256];
        for (int start = 0; start < 256; start++) {
            if (!hole[start] || seen[start]) continue;
            List<Integer> comp = new ArrayList<>();
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(start); seen[start] = true;
            boolean border = false;
            while (!q.isEmpty()) {
                int c = q.poll(); comp.add(c);
                int cx = c / 16, cz = c % 16;
                if (cx == 0 || cx == 15 || cz == 0 || cz == 15) border = true;
                int[][] nb = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
                for (int[] n : nb) {
                    if (n[0] < 0 || n[0] > 15 || n[1] < 0 || n[1] > 15) continue;
                    int k = n[0] * 16 + n[1];
                    if (hole[k] && !seen[k]) { seen[k] = true; q.add(k); }
                }
            }
            if (border || comp.size() > cfg.max_sealed_hole_area) continue; // intencional / tamanho desconhecido

            BlockState[] skin = neighborSkin(chunk, floor, hole, comp, fill, pos, minX, minZ);
            for (int c : comp) {
                int lx = c / 16, lz = c % 16, x = minX + lx, z = minZ + lz;
                int p = planned[c];
                int cur = floor.get(lx, lz);
                if (sp.factor(x, z, p, maxDelta) < 1.0) continue;
                boolean clear = true; // so preenche ar (nunca agua/lava)
                for (int y = cur; y < p && clear; y++) clear = chunk.getBlockState(pos.set(x, y, z)).isAir();
                if (!clear) continue;
                for (int y = cur; y < p; y++) {
                    int idx = (p - 1) - y;
                    TerrainRemodeler.set(chunk, lx, y, lz, idx < skin.length ? skin[idx] : fill, floor, surf);
                }
                stats.sealed++;
            }
        }
    }

    /** Cobertura (topo -> baixo, ate 4 blocos nao-pedra) da primeira coluna vizinha que nao e buraco. */
    private static BlockState[] neighborSkin(Chunk chunk, Heightmap floor, boolean[] hole, List<Integer> comp,
                                             BlockState fill, BlockPos.Mutable pos, int minX, int minZ) {
        for (int c : comp) {
            int cx = c / 16, cz = c % 16;
            int[][] nb = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
            for (int[] n : nb) {
                if (n[0] < 0 || n[0] > 15 || n[1] < 0 || n[1] > 15 || hole[n[0] * 16 + n[1]]) continue;
                int top = floor.get(n[0], n[1]) - 1;
                List<BlockState> out = new ArrayList<>();
                for (int y = top; y >= chunk.getBottomY() && out.size() < 4; y--) {
                    BlockState s = chunk.getBlockState(pos.set(minX + n[0], y, minZ + n[1]));
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.isOf(fill.getBlock())) break;
                    out.add(s);
                }
                return out.toArray(new BlockState[0]);
            }
        }
        return new BlockState[0];
    }
}
