package com.smoothsoft.terrain.mixin;

import com.smoothsoft.terrain.WorldGenerationHook;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inicio da etapa de features = logo DEPOIS dos carvers e ANTES de arvores/ores/estruturas:
 * fecha buracos pequenos de superficie (aberturas de caverna "sem sentido").
 *
 * Usa HEAD de generateFeatures (metodo concreto) em vez de TAIL de carve: no 1.21.11 o carve de
 * ChunkGenerator nao tem RETURN para o Mixin ancorar (crash "TAIL could not locate a valid RETURN").
 * require = 0 cobre apenas "metodo nao encontrado" (a selagem some, o resto funciona).
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(
        method = "generateFeatures(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/StructureAccessor;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void smoothsoftterrain$beforeFeatures(StructureWorldAccess world, Chunk chunk,
            StructureAccessor structures, CallbackInfo ci) {
        if ((Object) this instanceof NoiseChunkGenerator gen) {
            WorldGenerationHook.applyAfterCarve(gen, structures, chunk);
        }
    }
}
