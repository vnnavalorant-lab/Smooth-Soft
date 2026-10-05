package com.smoothsoft.terrain;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.StructureAccessor;

/**
 * Protege SOMENTE onde ha estrutura: usa a caixa de cada PECA (nao a caixa inteira da estrutura,
 * que numa vila cobre muito terreno vazio). Fator 0..1 por coluna: 0 dentro da peca, subindo
 * suavemente ate 1 a MARGIN blocos. Vale para estruturas vanilla e de mods (structure starts).
 */
final class StructureProtection {
    static final StructureProtection NONE = new StructureProtection(List.of());
    static final int MARGIN = 6;
    private final List<BlockBox> boxes;

    private StructureProtection(List<BlockBox> boxes) { this.boxes = boxes; }

    /** Lanca excecao se nao for possivel ler as estruturas -> o hook NAO modifica o chunk. */
    static StructureProtection collect(StructureAccessor sa, ChunkPos center) {
        List<BlockBox> out = new ArrayList<>();
        int cx = center.getStartX() >> 4, cz = center.getStartZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (StructureStart s : sa.getStructureStarts(new ChunkPos(cx + dx, cz + dz), st -> true)) {
                    if (!s.hasChildren()) continue;
                    List<StructurePiece> pieces = s.getChildren();
                    if (pieces.isEmpty()) out.add(s.getBoundingBox());
                    else for (StructurePiece p : pieces) out.add(p.getBoundingBox());
                }
            }
        }
        return new StructureProtection(out);
    }

    double factor(int x, int z, int surfaceY, int maxDelta) {
        double f = 1.0;
        for (BlockBox b : boxes) {
            if (b.getMaxY() < surfaceY - maxDelta - 6) continue; // bem abaixo do que podemos tocar
            int dx = Math.max(Math.max(b.getMinX() - x, x - b.getMaxX()), 0);
            int dz = Math.max(Math.max(b.getMinZ() - z, z - b.getMaxZ()), 0);
            int d = Math.max(dx, dz);
            if (d < MARGIN) f = Math.min(f, TerrainMath.smoothstep(d / (double) MARGIN));
        }
        return f;
    }
}
