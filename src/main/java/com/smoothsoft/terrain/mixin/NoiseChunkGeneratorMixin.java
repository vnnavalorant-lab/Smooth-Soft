package com.smoothsoft.terrain.mixin;

import com.smoothsoft.terrain.WorldGenerationHook;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unico ponto de entrada: fim do surface builder do NoiseChunkGenerator. Nesse instante o chunk
 * tem terreno + cobertura de bioma, mas ainda NAO tem carvers, features (arvores, ores, lagos),
 * nem estruturas colocadas: tudo isso ve o terreno ja remodelado.
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

    @Inject(
        method = "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("TAIL")
    )
    private void smoothsoftterrain$afterSurface(ChunkRegion region, StructureAccessor structures,
            NoiseConfig noiseConfig, Chunk chunk, CallbackInfo ci) {
        WorldGenerationHook.apply((NoiseChunkGenerator) (Object) this, region, noiseConfig, structures, chunk);
    }
}
