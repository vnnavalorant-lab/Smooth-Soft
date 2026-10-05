package com.smoothsoft.terrain;

final class TerrainMath {
    private TerrainMath() {}
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    static int clampInt(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    static double clamp01(double v) { return clamp(v, 0.0, 1.0); }
    static double smoothstep(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
}
