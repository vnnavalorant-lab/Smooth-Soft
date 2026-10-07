package com.smoothsoft.terrain;

/**
 * Verificacao do algoritmo SEM Minecraft: terreno sintetico estilo vanilla (montanhas serrilhadas,
 * colinas, ruido fino), mesma "seed", com o filtro OFF (original) vs ON (STRONG_NATURAL).
 * Mede: diferenca visivel, preservacao de altura/escala, rugosidade e continuidade entre chunks.
 */
public final class AlgorithmChecks {
    static final int SEA = 63;

    // ---------- terreno sintetico ----------
    static double hash(long s, int x, int z) {
        long h = s * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return ((h >>> 11) & 0xFFFFFFL) / (double) 0x1000000L;
    }
    static double vnoise(long s, double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = TerrainMath.smoothstep(x - xi), tz = TerrainMath.smoothstep(z - zi);
        double a = hash(s, xi, zi), b = hash(s, xi + 1, zi), c = hash(s, xi, zi + 1), d = hash(s, xi + 1, zi + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }
    static double fbm(long s, double x, double z, int oct) {
        double v = 0, amp = 0.5, tot = 0;
        for (int i = 0; i < oct; i++) { v += amp * vnoise(s + i * 31, x, z); tot += amp; x *= 2; z *= 2; amp *= 0.5; }
        return v / tot;
    }
    static int terrain(long seed, int x, int z) {
        double mask = TerrainMath.smoothstep((fbm(seed, x / 260.0, z / 260.0, 3) - 0.35) / 0.3);
        double hills = 12 * (fbm(seed + 7, x / 45.0, z / 45.0, 3) - 0.5) * 2;
        double ridge = 1 - Math.abs(2 * fbm(seed + 13, x / 90.0, z / 90.0, 3) - 1);
        double detail = 3.0 * (vnoise(seed + 21, x / 6.0, z / 6.0) - 0.5) * 2
                      + 1.5 * (vnoise(seed + 22, x / 2.5, z / 2.5) - 0.5) * 2;
        return (int) Math.round(72 + 28 * mask + 50 * mask * ridge * ridge + hills + detail * (0.6 + mask));
    }

    // ---------- pipeline por chunk (mesma matematica do mod) ----------
    static double[][] window(long seed, int cx, int cz, int m) {
        int n = 5 + 2 * m;
        double[][] R = new double[n][n];
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                R[a][b] = Math.max(terrain(seed, (cx * 4 + a - m) * 4, (cz * 4 + b - m) * 4), SEA);
        return R;
    }
    static double[][] inner(double[][] f, int m) {
        double[][] o = new double[5][5];
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) o[i][j] = f[m + i][m + j];
        return o;
    }
    record ChunkNodes(double[][] raw, double[][] hs, double[][] s, double[][] t) {}
    static ChunkNodes nodes(long seed, int cx, int cz, SmoothParams p, int margin) {
        double[][] R = window(seed, cx, cz, margin);
        var a = TerrainAnalyzer.analyze(R, p);
        var f = TerrainSmoother.smooth(R, a, p, null, null, (cx * 4 - margin) * 4.0, (cz * 4 - margin) * 4.0, SEA);
        return new ChunkNodes(inner(R, margin), inner(f.hs, margin), inner(f.strength, margin), inner(f.terrace, margin));
    }

    // ---------- checks ----------
    /** maior diferenca entre nos compartilhados por chunks vizinhos (deve ser ~0 = sem emenda). */
    public static double seamError(long seed, SmoothParams p, int margin) {
        double err = 0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                ChunkNodes a = nodes(seed, cx, cz, p, margin);
                ChunkNodes e = nodes(seed, cx + 1, cz, p, margin);
                ChunkNodes s = nodes(seed, cx, cz + 1, p, margin);
                for (int k = 0; k < 5; k++) {
                    err = Math.max(err, Math.abs(a.hs[4][k] - e.hs[0][k]));
                    err = Math.max(err, Math.abs(a.s[4][k] - e.s[0][k]));
                    err = Math.max(err, Math.abs(a.hs[k][4] - s.hs[k][0]));
                    err = Math.max(err, Math.abs(a.s[k][4] - s.s[k][0]));
                }
            }
        }
        return err;
    }

    /** Buracos sinteticos (tipo abertura de caverna / poco): 25% das celulas de 12x12 ganham um poco de 4-9 blocos. */
    static int pitDepth(long seed, int x, int z) {
        int cx = Math.floorDiv(x, 12), cz = Math.floorDiv(z, 12);
        if (hash(seed + 99, cx, cz) >= 0.25) return 0;
        int px = cx * 12 + 2 + (int) (hash(seed + 100, cx, cz) * 8);
        int pz = cz * 12 + 2 + (int) (hash(seed + 101, cx, cz) * 8);
        int dx = x - px, dz = z - pz;
        return dx * dx + dz * dz <= 3 ? 4 + (int) (hash(seed + 102, cx, cz) * 5) : 0;
    }

    public record Report(double meanOrig, double meanFinal, double meanAbsChange, double maxAbsChange,
                         double changedPct, double roughOrig, double roughFinal,
                         double maxOrig, double maxFinal, double seamStepOrig, double seamStepFinal,
                         double steps2Orig, double steps2Final, double steps3Orig, double steps3Final,
                         int pitsOrig, int pitsFinal, double treadOrig, double treadFinal) {}

    public static Report compare(long seed, SmoothParams p, int chunks, int maxDelta) {
        int margin = p.requiredMargin();
        int W = chunks * 16;
        double[][] orig = new double[W][W], fin = new double[W][W];
        for (int cx = 0; cx < chunks; cx++) {
            for (int cz = 0; cz < chunks; cz++) {
                ChunkNodes c = nodes(seed, cx, cz, p, margin);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = cx * 16 + lx, z = cz * 16 + lz;
                        double cur = terrain(seed, x, z) - pitDepth(seed, x, z);
                        orig[x][z] = cur;
                        if (cur <= SEA + 2) { fin[x][z] = cur; continue; }
                        double rb = ChunkBoundaryBlender.lerp2(c.raw, lx, lz);
                        double hb = ChunkBoundaryBlender.lerp2(c.hs, lx, lz);
                        double sb = ChunkBoundaryBlender.lerp2(c.s, lx, lz);
                        double ramp = TerrainMath.clamp01((cur - SEA - 2) / 4.0);
                        double tb = ChunkBoundaryBlender.lerp2(c.t, lx, lz);
                        double d = TerrainSmoother.finalHeight(cur, rb, hb, sb, tb, p.detailKeep(), ramp, true,
                            p.terraceStep(), TerrainSmoother.terraceOffset(x, z, Math.max(1, p.terraceStep()))) - cur;
                        fin[x][z] = cur + Math.round(TerrainMath.clamp(d, -maxDelta, maxDelta));
                    }
                }
            }
        }
        int lo = 40, hi = W - 40; // ignora a borda da regiao de teste
        double so = 0, sf = 0, sa = 0, mx = 0, ro = 0, rf = 0, mo = 0, mf = 0, chg = 0, cnt = 0;
        double stO = 0, stF = 0; int stN = 0;
        double s2o = 0, s2f = 0, s3o = 0, s3f = 0; int pitO = 0, pitF = 0;
        for (int x = lo; x < hi; x++) {
            for (int z = lo; z < hi; z++) {
                so += orig[x][z]; sf += fin[x][z];
                double d = Math.abs(fin[x][z] - orig[x][z]);
                sa += d; mx = Math.max(mx, d); if (d >= 1) chg++;
                mo = Math.max(mo, orig[x][z]); mf = Math.max(mf, fin[x][z]);
                ro += Math.abs(orig[x - 2][z] + orig[x + 2][z] - 2 * orig[x][z])
                    + Math.abs(orig[x][z - 2] + orig[x][z + 2] - 2 * orig[x][z]);
                rf += Math.abs(fin[x - 2][z] + fin[x + 2][z] - 2 * fin[x][z])
                    + Math.abs(fin[x][z - 2] + fin[x][z + 2] - 2 * fin[x][z]);
                cnt++;
                if (x % 16 == 15) { stO += Math.abs(orig[x + 1][z] - orig[x][z]); stF += Math.abs(fin[x + 1][z] - fin[x][z]); stN++; }
                double[] dO = {orig[x + 1][z] - orig[x][z], orig[x][z + 1] - orig[x][z]};
                double[] dF = {fin[x + 1][z] - fin[x][z], fin[x][z + 1] - fin[x][z]};
                for (double v : dO) { if (Math.abs(v) >= 2) s2o++; if (Math.abs(v) >= 3) s3o++; }
                for (double v : dF) { if (Math.abs(v) >= 2) s2f++; if (Math.abs(v) >= 3) s3f++; }
                if (isPit(orig, x, z)) pitO++;
                if (isPit(fin, x, z)) pitF++;
            }
        }
        double pairs = 2 * cnt;
        double trO = treadRun(orig, lo, hi), trF = treadRun(fin, lo, hi);
        return new Report(so / cnt, sf / cnt, sa / cnt, mx, 100 * chg / cnt, ro / cnt, rf / cnt, mo, mf,
            stO / stN, stF / stN, 100 * s2o / pairs, 100 * s2f / pairs, 100 * s3o / pairs, 100 * s3f / pairs, pitO, pitF, trO, trF);
    }

    /** Em encostas suaves (inclinacao 0.10-0.45 em 8 blocos): comprimento medio (blocos) dos patamares planos ao longo de x e z. */
    static double treadRun(double[][] h, int lo, int hi) {
        double sum = 0; int runs = 0;
        for (int dir = 0; dir < 2; dir++) {
            for (int a = lo; a < hi; a++) {
                int start = lo;
                for (int b = lo + 1; b <= hi; b++) {
                    double v = dir == 0 ? h[b < hi ? b : hi - 1][a] : h[a][b < hi ? b : hi - 1];
                    double sv = dir == 0 ? h[start][a] : h[a][start];
                    if (b == hi || v != sv) {
                        int mid = (start + b - 1) / 2;
                        double g = dir == 0 ? Math.abs(h[mid + 4][a] - h[mid - 4][a]) / 8.0
                                            : Math.abs(h[a][mid + 4] - h[a][mid - 4]) / 8.0;
                        if (g >= 0.10 && g <= 0.45 && sv > SEA + 5) { sum += b - start; runs++; }
                        start = b;
                    }
                }
            }
        }
        return runs == 0 ? 0 : sum / runs;
    }

    /** coluna pelo menos 2 blocos abaixo de TODOS os 4 vizinhos */
    static boolean isPit(double[][] h, int x, int z) {
        double c = h[x][z];
        return h[x + 1][z] - c >= 2 && h[x - 1][z] - c >= 2 && h[x][z + 1] - c >= 2 && h[x][z - 1] - c >= 2;
    }

    static final Object[][] MODES = {
        {"SUBTLE",         SmoothParams.of(0.30,  8, 16, 28, 0.45, 1.4, 0, 0.0, 5.0)},
        {"NATURAL",        SmoothParams.of(0.45, 10, 20, 32, 0.38, 1.1, 1, 0.35, 5.0)},
        {"STRONG_NATURAL", SmoothParams.of(0.65, 12, 24, 40, 0.30, 0.9, 1, 0.60, 5.0)},
        {"VERY_SMOOTH",    SmoothParams.of(0.85, 14, 28, 44, 0.22, 0.7, 1, 0.75, 5.0)},
        {"SOFT_WORLD",     SmoothParams.of(0.90, 16, 32, 46, 0.12, 0.7, 1, 0.85, 5.0)},
        {"SOFT sem terraco", SmoothParams.of(0.90, 16, 32, 46, 0.12, 0.7, 0, 0.0, 5.0)},
    };

    public static void main(String[] args) {
        for (Object[] m : MODES) {
            SmoothParams p = (SmoothParams) m[1];
            int margin = p.requiredMargin();
            System.out.printf("== %s | margem %d nos (%d blocos) | erro de emenda %.1e%n",
                m[0], margin, margin * 4, seamError(42, p, margin));
            for (long seed : new long[] {1, 42, 777}) {
                Report r = compare(seed, p, 16, 24);
                System.out.printf("  seed %-3d media %.1f->%.1f | |mud| %.2f max %.0f | rugos. -%.0f%% | pico %.0f->%.0f | degraus>=2: %.1f%%->%.1f%% >=3: %.1f%%->%.1f%% | pocos %d->%d | patamar medio: %.1f->%.1f blocos%n",
                    seed, r.meanOrig, r.meanFinal, r.meanAbsChange, r.maxAbsChange,
                    100 * (1 - r.roughFinal / r.roughOrig), r.maxOrig, r.maxFinal,
                    r.steps2Orig, r.steps2Final, r.steps3Orig, r.steps3Final, r.pitsOrig, r.pitsFinal, r.treadOrig, r.treadFinal);
            }
        }
    }
}
