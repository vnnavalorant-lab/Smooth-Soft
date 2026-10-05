package com.smoothsoft.terrain;

/** Estatisticas por chunk para debug_visualization. */
final class ChunkStats {
    int evaluated, modified, grass, support, sealed;
    double sumOrig, sumFinal, sumAbs, maxAbs, sumStrength;

    String format(int cx, int cz, double strength) {
        double n = Math.max(1, evaluated);
        return String.format(java.util.Locale.ROOT,
            "[SmoothSoftTerrain]%nChunk: %d %d%nAverage Original Height: %.1f%nAverage Smoothed Height: %.1f%n"
          + "Average Height Difference: %.2f%nMaximum Difference: %.0f%nSmooth Strength: %.2f (media dos nos: %.2f)%n"
          + "Columns modified: %d / %d evaluated (of 256)%nGrass blocks fixed: %d%nSupport blocks added: %d%nHole columns sealed: %d",
            cx, cz, sumOrig / n, sumFinal / n, sumAbs / n, maxAbs, strength, sumStrength / n,
            modified, evaluated, grass, support, sealed);
    }
}
