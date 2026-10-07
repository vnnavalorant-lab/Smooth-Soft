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
        double[][] terrace;   // 0..1: quanto aquele no virou terraco
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

    /**
     * biome: fator 0..1 por no (ou null = 1); terraceScale: 0..1 por no (ou null = 1);
     * ox/oz: coordenada de MUNDO (blocos) do no [0][0]; sea: nivel do mar.
     */
    static Fields smooth(double[][] R, TerrainAnalyzer.Analysis a, SmoothParams p, double[][] biome,
                         double[][] terraceScale, double ox, double oz, int sea) {
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
        applyTerraces(out, prot, p, terraceScale, ox, oz, sea);
        // nas zonas de terraco a inclinacao alvo e a do patamar (ex.: degrau de 1 bloco a cada ~5 blocos)
        double[][] lim = new double[n][n];
        double tgt = Math.min(p.maxSlope(), p.terraceSlope());
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                double t = out.terrace[x][z];
                lim[x][z] = p.maxSlope() * (1.0 - t) + tgt * t;
            }
        limitSlopes(out.hs, prot, lim);
        return out;
    }

    /** Patamares de altura s com degrau suave (riser 40% do ciclo, centrado; 60% e patamar plano), media preservada. */
    static double terrace(double h, double s) {
        double u = h / s, k = Math.floor(u), f = u - k;
        return s * (k + TerrainMath.smoothstep((f - 0.30) / 0.40));
    }

    /** Deslocamento (blocos) de baixa frequencia que torna os contornos dos terracos irregulares. */
    static double terraceOffset(double wx, double wz, double step) {
        return 0.6 * step * (2.0 * NoiseUtil.fbm(wx / 90.0 + 17, wz / 90.0 - 31) - 1.0);
    }

    /**
     * Terracos largos: calcula, por no, QUANTO terraco aplicar (T, 0..1): encosta suave (nem plano nem
     * ingreme), longe da costa, dentro de manchas de baixa frequencia (o resto fica rampa continua).
     * O degrau em si e aplicado por coluna em finalHeight (resolucao de bloco => patamares realmente planos).
     * Nos de encosta suave (nem plano nem ingreme) sao puxados para patamares
     * horizontais. O deslocamento `off` (ruido de baixa frequencia, em coordenadas de mundo) faz os
     * contornos serem irregulares e a largura variar; a mascara `mask` deixa trechos so com rampa continua.
     * Os degraus resultantes ainda passam pelo limitador de slope (rampas andaveis).
     */
    private static void applyTerraces(Fields out, double[][] prot, SmoothParams p, double[][] scale,
                                      double ox, double oz, int sea) {
        int n = out.hs.length;
        out.terrace = new double[n][n];
        if (p.terraceStep() <= 0 || p.terraceStrength() <= 0) return;
        double[][] src = new double[n][];
        for (int i = 0; i < n; i++) src[i] = out.hs[i].clone();
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double h = src[x][z];
                double wx = ox + x * 4.0, wz = oz + z * 4.0;
                double gx = (GridMath.at(src, x + 1, z) - GridMath.at(src, x - 1, z)) / 8.0;
                double gz = (GridMath.at(src, x, z + 1) - GridMath.at(src, x, z - 1)) / 8.0;
                double grad = Math.hypot(gx, gz);
                double bell = TerrainMath.smoothstep((grad - 0.04) / 0.10)
                            * (1.0 - TerrainMath.smoothstep((grad - 0.70) / 0.50));
                double mask = TerrainMath.smoothstep((NoiseUtil.vnoise(wx / 140.0 + 5, wz / 140.0 + 9) - 0.15) / 0.35);
                double seaGate = TerrainMath.smoothstep((h - sea - 3) / 6.0);
                double sc = scale == null ? 1.0 : scale[x][z];
                double t = p.terraceStrength() * mask * bell * (1.0 - prot[x][z]) * seaGate * sc;
                out.terrace[x][z] = t;
            }
        }
    }

    /**
     * Limitador de inclinacao (erosao termica): onde dois nos vizinhos diferem mais que
     * maxSlope * distancia, ambos andam um em direcao ao outro (soma conservada => media preservada).
     * Penhascos coerentes (prot alto) tolerados ate ~3x mais ingremes. SLOPE_ITERS iteracoes.
     */
    static void limitSlopes(double[][] h, double[][] prot, double[][] maxSlope) {
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
                        double lim = Math.min(maxSlope[x][z], maxSlope[x2][z2]) * dist * (1.0 + 2.0 * Math.max(prot[x][z], prot[x2][z2]));
                        double diff = h[x][z] - h[x2][z2];
                        double ex = Math.abs(diff) - lim;
                        if (ex <= 0) continue;
                        double t = 0.12 * ex * Math.signum(diff); // 8 vizinhos x 0.12 < 1: estavel (sem oscilacao)
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
    static double finalHeight(double cur, double rb, double hb, double sb, double tb, double detailKeep,
                              double gate, boolean fillPits, int terraceStep, double terraceOff) {
        double s = Math.max(sb, 0.97 * tb);          // onde ha terraco, segue o campo suavizado quase por inteiro
        double dk = detailKeep * (1.0 - tb);          // e sem ruido fino para os patamares ficarem planos
        if (fillPits) {
            // poco/buraco: coluna bem abaixo da superficie suave ao redor. Preenche ate a superficie,
            // mas depressoes MUITO fundas (>~12) sao tratadas como aberturas intencionais (caverna/ravina).
            double delta = hb - cur;
            double pit = TerrainMath.clamp01((delta - 2.0) / 3.0) * (1.0 - TerrainMath.clamp01((delta - 8.0) / 8.0));
            s = Math.max(s, 0.95 * pit);
        }
        s *= gate;
        double smoothCol = hb + dk * (cur - rb);
        if (terraceStep > 0 && tb > 0.0) {
            // patamares planos (multiplos de terraceStep) ligados por rampas curtas; contornos deslocados por ruido
            smoothCol += tb * (terrace(smoothCol + terraceOff, terraceStep) - smoothCol);
        }
        return cur + s * (smoothCol - cur);
    }
}
