package com.smoothsoft.terrain;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Etapa final, depois de TODA a remodelacao: DIRT natural no topo da coluna (ceu aberto,
 * ar diretamente acima) vira GRASS_BLOCK (ou MYCELIUM em mushroom_fields). Blocos reais.
 * Nao toca: interiores de caverna (nao sao topo de coluna), dirt coberto, areas de estrutura,
 * biomas sem grama (config) e nada alem do bloco do topo.
 */
final class SurfaceValidationPass {
    private SurfaceValidationPass() {}

    static void run(Chunk chunk, StructureProtection sp, TerrainConfig cfg, int sea, ChunkStats stats) {
        Set<String> skip = new HashSet<>(cfg.no_grass_biomes);
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        int minX = chunk.getPos().getStartX(), minZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int y = floor.get(lx, lz) - 1; // topo solido da coluna
                if (y <= sea) continue;
                int x = minX + lx, z = minZ + lz;
                BlockState s = chunk.getBlockState(pos.set(x, y, z));
                boolean dirt = s.isOf(Blocks.DIRT) || (cfg.convert_coarse_dirt && s.isOf(Blocks.COARSE_DIRT));
                if (!dirt) continue;
                if (!chunk.getBlockState(pos.set(x, y + 1, z)).isAir()) continue; // precisa de ar acima
                if (sp.factor(x, z, y, 0) < 1.0) continue;                         // area de estrutura
                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(x >> 2, y >> 2, z >> 2);
                String id = BiomeProtection.id(biome);
                if (skip.contains(id)) continue;
                BlockState cover = "minecraft:mushroom_fields".equals(id)
                    ? Blocks.MYCELIUM.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                ChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                section.setBlockState(lx, y & 15, lz, cover, false);
                stats.grass++;
            }
        }
    }
}
