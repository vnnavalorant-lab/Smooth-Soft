package com.smoothsoft.terrain;

/**
 * Suavizacao multi-escala do heightmap (nunca de blocos soltos).
 *
 * O campo R e decomposto em bandas que somam exatamente R:
 *   R = Macro + (Regional-Macro) + (Local-Regional) + (Micro-Local) + (R-Micro)
 * Cada banda e atenuada de acordo com a "necessidade de suavizar" do ponto
 * (irregularidade, slope, curvatura; penhascos legitimos reduzem a necessidade).
 * Como as bandas grandes sao quase preservadas, a forma/altura/media da regiao ficam
 * e so a silhueta e arredondada; o ruido fino nao some (banda de detalhe retida).
 */
final class TerrainSmoother {
    private TerrainSmoother() {}

    static final class Fields {
        double[][] hs;        // altura suavizada alvo (nos)
        double[][] strength;  // forca do blend com o original, 0..0.92 (nos)
        double[][] irr;
    }

    // retencao de cada banda em [terreno ja bom (e=0) , terreno muito quebrado (e=1)]
    private static final double[] K_NOISE = {0.30, 0.00};   // R - Micro
    private static final double[] K_MICRO = {0.45, 0.00};   // Micro - Local
    private static final double[] K_LOCAL = {0.70, 0.15};   // Local - Regional
    private static final double[] K_REG   = {0.95, 0.45};   // Regional - Macro

    private static double keep(double[] k, double e, double f) {
        double base = k[0] + (k[1] - k[0]) * e;
        return TerrainMath.clamp01(1.0 - (1.0 - base) * f);
    }

    /** biome: fator 0..1 por no (ou null = 1). */
    static Fields smooth(double[][] R, TerrainAnalyzer.Analysis a, SmoothParams p, double[][] biome) {
        int n = R.length;
        double f = p.strengthFactor();
        Fields out = new Fields();
        out.hs = new double[n][n];
        out.strength = new double[n][n];
        out.irr = a.irr;
        double[][] prot = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double irr = a.irr[x][z];
                double slopeEx = TerrainMath.clamp01((a.gradM[x][z] - 1.0) / 2.0);  // degraus ingremes
                double curvEx = TerrainMath.clamp01((a.lap[x][z] - 2.5) / 6.0);     // quinas / mudancas bruscas
                double e = TerrainMath.clamp01(0.8 * irr + 0.3 * Math.max(slopeEx, curvEx));
                // penhasco legitimo: gradiente alto que sobrevive ao blur local -> suaviza menos (nunca zero)
                double pr = TerrainMath.clamp01((a.gradL[x][z] - 2.2) / 1.5);
                prot[x][z] = pr;
                e *= (1.0 - 0.5 * pr);

                double kn = keep(K_NOISE, e, f), km = keep(K_MICRO, e, f);
                double kl = keep(K_LOCAL, e, f), kr = keep(K_REG, e, f);
                out.hs[x][z] = a.gc[x][z]
                    + kr * (a.gr[x][z] - a.gc[x][z])
                    + kl * (a.gl[x][z] - a.gr[x][z])
                    + km * (a.gm[x][z] - a.gl[x][z])
                    + kn * (R[x][z] - a.gm[x][z]);

                // preserva a altura de picos: o topo nunca desce mais que ~12% da proeminencia (+3 blocos)
                double floor = R[x][z] - (3.0 + 0.12 * Math.max(0.0, R[x][z] - a.gc[x][z]));
                if (out.hs[x][z] < floor) out.hs[x][z] = floor;

                // blend com o original: 0.15 (ja suave) .. 0.70 (muito quebrado), escalado pelo modo
                double s = (0.40 + 0.50 * TerrainMath.smoothstep(irr)) * f * (1.0 - 0.4 * pr);
                if (biome != null) s *= biome[x][z];
                // onde a inclinacao original passa do limite andavel, o resultado segue o campo suavizado
                double steep = TerrainMath.clamp01((a.gradM[x][z] - 0.8 * p.maxSlope()) / (0.8 * p.maxSlope()));
                s = s + (0.92 - s) * steep * (1.0 - 0.6 * pr);
                out.strength[x][z] = Math.min(0.92, s);
            }
        }
        limitSlopes(out.hs, prot, p.maxSlope());
        return out;
    }

    /**
     * Limitador de inclinacao (erosao termica): onde dois nos vizinhos diferem mais que
     * maxSlope * distancia, ambos andam um em direcao ao outro (soma conservada => media preservada).
     * Penhascos coerentes (prot alto) tolerados ate ~3x mais ingremes. SLOPE_ITERS iteracoes.
     */
    static void limitSlopes(double[][] h, double[][] prot, double maxSlope) {
        int n = h.length;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        double[][] d = new double[n][n];
        for (int it = 0; it < SmoothParams.SLOPE_ITERS; it++) {
            for (double[] row : d) java.util.Arrays.fill(row, 0.0);
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    for (int[] dir : dirs) {
                        int x2 = x + dir[0], z2 = z + dir[1];
                        if (x2 < 0 || z2 < 0 || x2 >= n || z2 >= n) continue;
                        double dist = HEIGHT_CELL * (dir[0] != 0 && dir[1] != 0 ? Math.sqrt(2) : 1.0);
                        double lim = maxSlope * dist * (1.0 + 2.0 * Math.max(prot[x][z], prot[x2][z2]));
                        double diff = h[x][z] - h[x2][z2];
                        double ex = Math.abs(diff) - lim;
                        if (ex <= 0) continue;
                        double t = 0.2 * ex * Math.signum(diff);
                        d[x][z] -= t;
                        d[x2][z2] += t;
                    }
                }
            }
            for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) h[x][z] += d[x][z];
        }
    }

    private static final double HEIGHT_CELL = 4.0;

    /**
     * Altura final de uma coluna: FinalHeight = lerp(Original, Smooth, S).
     * cur = altura original da coluna; rb/hb/sb = cru/suavizado/forca interpolados nesse ponto.
     * O detalhe fino (cur - rb) e parcialmente restaurado dentro do Smooth.
     */
    static double finalHeight(double cur, double rb, double hb, double sb, double detailKeep,
                              double gate, boolean fillPits) {
        double s = sb;
        if (fillPits) {
            // poco/buraco: coluna bem abaixo da superficie suave ao redor. Preenche ate a superficie,
            // mas depressoes MUITO fundas (>~12) sao tratadas como aberturas intencionais (caverna/ravina).
            double delta = hb - cur;
            double pit = TerrainMath.clamp01((delta - 2.0) / 3.0) * (1.0 - TerrainMath.clamp01((delta - 8.0) / 8.0));
            s = Math.max(s, 0.95 * pit);
        }
        s *= gate;
        double smoothCol = hb + detailKeep * (cur - rb);
        return cur + s * (smoothCol - cur);
    }
}
