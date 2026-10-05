package com.smoothsoft.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/**
 * Terreno superficial sempre apoiado: se a camada solida do topo tem menos de
 * min_surface_support blocos e logo abaixo existe um vazio de AR, o vazio e preenchido de cima
 * para baixo ate dar a espessura minima (ou ate o vazio acabar). Efeito:
 *  - fragmentos finos / "terreno flutuante" e frestas pequenas viram terreno continuo;
 *  - bolsoes de caverna rasos sob a superficie fecham; cavernas grandes ficam, so com teto mais grosso.
 * Roda ANTES da remodelagem (para que o preenchimento de pocos tenha base solida) e antes dos
 * carvers. Nao mexe em agua/aquiferos nem perto de estruturas.
 */
final class SurfaceSupportPass {
    private SurfaceSupportPass() {}

    static void run(NoiseChunkGenerator gen, Chunk chunk, StructureProtection sp, TerrainConfig cfg,
                    int sea, ChunkStats stats) {
        int minSupport = TerrainMath.clampInt(cfg.min_surface_support, 0, 12);
        if (minSupport <= 0) return;
        BlockState fill = gen.getSettings().value().defaultBlock();
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surf = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        int minX = chunk.getPos().getStartX(), minZ = chunk.getPos().getStartZ();
        int bottom = chunk.getBottomY();
        int maxDelta = TerrainMath.clampInt(cfg.max_height_change, 1, 48);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int cur = floor.get(lx, lz);
                if (cur <= sea + 2) continue;
                int x = minX + lx, z = minZ + lz;
                if (sp.factor(x, z, cur, maxDelta) < 1.0) continue;

                int t = 0, y = cur - 1;
                while (y >= bottom && t < minSupport && TerrainRemodeler.solidAt(chunk, pos, x, y, z)) { t++; y--; }
                if (t >= minSupport || y < bottom) continue;
                if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) continue; // agua/aquifero/outro: nao mexe

                int need = minSupport - t;
                for (int k = 0; k < need && y >= bottom && chunk.getBlockState(pos.set(x, y, z)).isAir(); k++, y--) {
                    TerrainRemodeler.set(chunk, lx, y, lz, fill, floor, surf);
                    stats.support++;
                }
            }
        }
    }
}
