package com.smoothsoft.terrain;

/**
 * A continuidade entre chunks vem de dois fatos: (1) os nos da grade sao globais, entao
 * dois chunks vizinhos calculam exatamente os mesmos valores nas bordas; (2) a interpolacao
 * bilinear abaixo e uma funcao continua das coordenadas do mundo.
 */
final class ChunkBoundaryBlender {
    private ChunkBoundaryBlender() {}

    /** a = valores nos 5x5 nos do chunk; (lx,lz) = coluna local 0..15. */
    static double lerp2(double[][] a, int lx, int lz) {
        int i = lx >> 2, j = lz >> 2;
        double tx = (lx & 3) / 4.0, tz = (lz & 3) / 4.0;
        double v00 = a[i][j], v10 = a[i + 1][j], v01 = a[i][j + 1], v11 = a[i + 1][j + 1];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
    }
}
