package com.smoothsoft.terrain;

/** Ruido de valor deterministico em coordenadas de MUNDO (sem estado, sem Minecraft). */
final class NoiseUtil {
    private NoiseUtil() {}

    static double hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static double vnoise(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = TerrainMath.smoothstep(x - xi), tz = TerrainMath.smoothstep(z - zi);
        double a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), d = hash(xi + 1, zi + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    /** 2 oitavas, 0..1 */
    static double fbm(double x, double z) {
        return 0.65 * vnoise(x, z) + 0.35 * vnoise(2 * x + 3.7, 2 * z + 7.1);
    }
}
