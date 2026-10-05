package com.smoothsoft.terrain.mixin;

import com.smoothsoft.terrain.WorldGenerationHook;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fim da etapa de carvers: fecha buracos pequenos de superficie (aberturas de caverna "sem sentido").
 * require = 0: se esta assinatura nao existir nesta versao, o mod continua funcionando sem a selagem.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(
        method = "carve(Lnet/minecraft/world/ChunkRegion;JLnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/biome/source/BiomeAccess;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void smoothsoftterrain$afterCarve(ChunkRegion region, long seed, NoiseConfig noiseConfig,
            BiomeAccess biomeAccess, StructureAccessor structures, Chunk chunk, CallbackInfo ci) {
        if ((Object) this instanceof NoiseChunkGenerator gen) {
            WorldGenerationHook.applyAfterCarve(gen, region, noiseConfig, structures, chunk);
        }
    }
}
