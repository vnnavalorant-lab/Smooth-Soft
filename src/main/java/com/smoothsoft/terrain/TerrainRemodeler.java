package com.smoothsoft.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/**
 * Aplica o heightmap final ao chunk: Original -> Smoothed -> Blended -> Final Terrain.
 * Roda DEPOIS do surface builder e ANTES de carvers/features/estruturas. Para nao perder a
 * cobertura de bioma (grama, areia, podzol...), cada coluna carrega junto a sua "pele"
 * (blocos nao-pedra do topo): ao subir a coluna a pele sobe, ao descer a pele reaparece no novo topo.
 */
final class TerrainRemodeler {
    private static final int MAX_SKIN = 8;

    private TerrainRemodeler() {}

    static void apply(NoiseChunkGenerator gen, Chunk chunk, double[][] raw, double[][] hs, double[][] st,
                      double[][] tr, StructureProtection sp, TerrainConfig cfg, SmoothParams params, int sea, ChunkStats stats) {
        BlockState fill = gen.getSettings().value().defaultBlock();
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surf = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        int minX = chunk.getPos().getStartX(), minZ = chunk.getPos().getStartZ();
        int bottom = chunk.getBottomY();
        int maxY = bottom + chunk.getHeight() - 1;
        int maxDelta = TerrainMath.clampInt(cfg.max_height_change, 1, 48);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState[] skin = new BlockState[MAX_SKIN];

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int cur = floor.get(lx, lz); // primeiro Y livre acima do piso
                int top = cur - 1;
                if (cur <= sea + 2) continue; // agua / praia: intocado
                int x = minX + lx, z = minZ + lz;
                stats.evaluated++;
                stats.sumOrig += cur;

                double sf = sp.factor(x, z, cur, maxDelta);
                double rb = ChunkBoundaryBlender.lerp2(raw, lx, lz);
                double hb = ChunkBoundaryBlender.lerp2(hs, lx, lz);
                double sb = ChunkBoundaryBlender.lerp2(st, lx, lz);
                double tb = ChunkBoundaryBlender.lerp2(tr, lx, lz);
                stats.sumStrength += sb;
                double ramp = cfg.preserve_water ? TerrainMath.clamp01((cur - sea - 2) / 4.0) : 1.0;

                double d = TerrainSmoother.finalHeight(cur, rb, hb, sb, tb, params.detailKeep(), ramp * sf, cfg.fill_pits,
                    params.terraceStep(), TerrainSmoother.terraceOffset(x, z, Math.max(1, params.terraceStep()))) - cur;
                int step = (int) Math.round(TerrainMath.clamp(d, -maxDelta, maxDelta));
                int applied = 0;

                if (step != 0) applied = remodelColumn(chunk, pos, skin, fill, floor, surf, lx, lz, x, z,
                    top, step, sea, maxY, bottom, maxDelta, cfg);

                stats.sumFinal += cur + applied;
                double ad = Math.abs(applied);
                stats.sumAbs += ad;
                if (ad > stats.maxAbs) stats.maxAbs = ad;
                if (applied != 0) stats.modified++;
            }
        }
    }

    /** Retorna a variacao efetivamente aplicada (pode ser menor que a pedida). */
    private static int remodelColumn(Chunk chunk, BlockPos.Mutable pos, BlockState[] skin, BlockState fill,
                                     Heightmap floor, Heightmap surf, int lx, int lz, int x, int z, int top,
                                     int step, int sea, int maxY, int bottom, int maxDelta, TerrainConfig cfg) {
        // pele: blocos do topo que nao sao a pedra padrao
        int t = 0;
        for (int y = top; y >= bottom && t < MAX_SKIN; y--) {
            BlockState s = chunk.getBlockState(pos.set(x, y, z));
            if (s.isAir() || !s.getFluidState().isEmpty() || s.isOf(fill.getBlock()) || s.isOf(Blocks.DEEPSLATE)) break;
            skin[t++] = s;
        }

        int delta;
        if (step < 0) {
            // profundidade solida continua abaixo do topo (cavernas / aquiferos limitam o corte)
            int solid = 0;
            for (int y = top; y >= bottom && solid < maxDelta + 4; y--) {
                if (!solidAt(chunk, pos, x, y, z)) break;
                solid++;
            }
            int allowed = -step;
            allowed = Math.min(allowed, top + 1 - (sea + 2)); // novo topo >= nivel do mar + 2
            if (cfg.preserve_caves) allowed = Math.min(allowed, solid - 3); // sempre sobra teto de 3 blocos
            if (allowed < 1) return 0;
            delta = -allowed;
        } else {
            if (cfg.preserve_caves) {
                for (int y = top; y >= top - 2; y--) if (!solidAt(chunk, pos, x, y, z)) return 0;
            }
            int air = 0;
            for (int y = top + 1; y <= maxY && air < step; y++) {
                if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) break; // agua / outro bloco: para
                air++;
            }
            delta = Math.min(step, Math.min(air, maxY - top));
            if (delta < 1) return 0;
        }

        int newTop = top + delta;
        int lo = Math.min(top, newTop) - t + 1;
        int hi = Math.max(top, newTop);
        for (int y = lo; y <= hi; y++) {
            BlockState state;
            if (y > newTop) state = Blocks.AIR.getDefaultState();
            else {
                int idx = newTop - y;
                state = idx < t ? skin[idx] : fill;
            }
            set(chunk, lx, y, lz, state, floor, surf);
        }
        return delta;
    }

    static boolean solidAt(Chunk c, BlockPos.Mutable p, int x, int y, int z) {
        if (y < c.getBottomY()) return false;
        BlockState s = c.getBlockState(p.set(x, y, z));
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    static void set(Chunk chunk, int lx, int y, int lz, BlockState state, Heightmap floor, Heightmap surf) {
        ChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(lx, y & 15, lz, state, false);
        floor.trackUpdate(lx, y, lz, state);
        surf.trackUpdate(lx, y, lz, state);
    }
}
