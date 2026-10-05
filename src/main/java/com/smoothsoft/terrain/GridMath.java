package com.smoothsoft.terrain;

/** Operacoes sobre campos 2D quadrados double[x][z]. Sem dependencias do Minecraft. */
final class GridMath {
    private GridMath() {}

    static double at(double[][] a, int x, int z) {
        int n = a.length;
        return a[x < 0 ? 0 : Math.min(x, n - 1)][z < 0 ? 0 : Math.min(z, n - 1)];
    }

    /** Gaussiana separavel (bordas replicadas). hw = meia-largura em nos, sigma em nos. */
    static double[][] gaussian(double[][] a, int hw, double sigma) {
        int n = a.length;
        double[] k = new double[2 * hw + 1];
        double sum = 0;
        for (int i = -hw; i <= hw; i++) { k[i + hw] = Math.exp(-(i * i) / (2 * sigma * sigma)); sum += k[i + hw]; }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        double[][] t = new double[n][n];
        double[][] o = new double[n][n];
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                double s = 0;
                for (int u = -hw; u <= hw; u++) s += k[u + hw] * a[Math.min(Math.max(x + u, 0), n - 1)][z];
                t[x][z] = s;
            }
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                double s = 0;
                for (int u = -hw; u <= hw; u++) s += k[u + hw] * t[x][Math.min(Math.max(z + u, 0), n - 1)];
                o[x][z] = s;
            }
        return o;
    }
}
