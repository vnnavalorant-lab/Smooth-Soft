package com.smoothsoft.terrain;

/**
 * Analise do campo de alturas (nos a cada 4 blocos): quatro escalas gaussianas
 * (MICRO / LOCAL / REGIONAL / MACRO), ruido, slope, curvatura e irregularidade.
 */
final class TerrainAnalyzer {
    private TerrainAnalyzer() {}

    static final class Analysis {
        double[][] gm, gl, gr, gc;   // micro, local, regional, macro
        double[][] irr;              // 0 = natural/suave, 1 = muito quebrado
        double[][] lap;              // |curvatura| (blocos por celula^2) em gm
        double[][] gradM, gradL;     // slope (blocos/bloco) em gm e gl
    }

    static Analysis analyze(double[][] R, SmoothParams p) {
        int n = R.length;
        Analysis a = new Analysis();
        a.gm = GridMath.gaussian(R, SmoothParams.hw(p.microR()), SmoothParams.sigma(p.microR()));
        a.gl = GridMath.gaussian(R, SmoothParams.hw(p.localR()), SmoothParams.sigma(p.localR()));
        a.gr = GridMath.gaussian(R, SmoothParams.hw(p.regionalR()), SmoothParams.sigma(p.regionalR()));
        a.gc = GridMath.gaussian(R, SmoothParams.hw(p.macroR()), SmoothParams.sigma(p.macroR()));

        double[][] abs = new double[n][n];
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) abs[x][z] = Math.abs(R[x][z] - a.gm[x][z]);
        double[][] hf = GridMath.gaussian(abs, 2, 1.0); // amplitude do ruido fino

        a.irr = new double[n][n];
        a.lap = new double[n][n];
        a.gradM = new double[n][n];
        a.gradL = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double[][] g = a.gm;
                double lap = Math.abs(GridMath.at(g, x + 1, z) + GridMath.at(g, x - 1, z)
                    + GridMath.at(g, x, z + 1) + GridMath.at(g, x, z - 1) - 4 * g[x][z]);
                double gx = (GridMath.at(g, x + 1, z) - GridMath.at(g, x - 1, z)) / 8.0;
                double gz = (GridMath.at(g, x, z + 1) - GridMath.at(g, x, z - 1)) / 8.0;
                double gradM = Math.hypot(gx, gz);
                double[][] l = a.gl;
                double lx = (GridMath.at(l, x + 1, z) - GridMath.at(l, x - 1, z)) / 8.0;
                double lz = (GridMath.at(l, x, z + 1) - GridMath.at(l, x, z - 1)) / 8.0;
                a.lap[x][z] = lap;
                a.gradM[x][z] = gradM;
                a.gradL[x][z] = Math.hypot(lx, lz);
                a.irr[x][z] = TerrainMath.clamp01(
                    0.50 * TerrainMath.clamp01(hf[x][z] / 2.0)
                  + 0.30 * TerrainMath.clamp01(lap / 4.0)
                  + 0.30 * TerrainMath.clamp01((gradM - 0.6) / 1.2));
            }
        }
        return a;
    }
}
