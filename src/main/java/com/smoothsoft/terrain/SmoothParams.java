package com.smoothsoft.terrain;

/**
 * Parametros do algoritmo (raios em BLOCOS). Sem dependencias do Minecraft.
 * micro 2-4, local 5-10, regional 12-32, macro 25-48.
 * maxSlope = inclinacao maxima desejada (blocos de altura por bloco horizontal).
 * terraceStep = altura (blocos) de cada patamar (1 = degraus de 1 bloco como na referencia); 0 desliga.
 * terraceStrength 0..1. terraceSlope = inclinacao media alvo nas zonas de terraco (= step / largura do patamar).
 */
record SmoothParams(double microR, double localR, double regionalR, double macroR,
                    double strengthFactor, double detailKeep, double maxSlope,
                    int terraceStep, double terraceStrength, double terraceSlope) {

    static final int SLOPE_ITERS = 12;

    static int hw(double rBlocks) { return Math.max(1, (int) Math.ceil(rBlocks / 4.0)); }
    static double sigma(double rBlocks) { return Math.max(0.7, rBlocks / 8.0); }

    /** Margem (nos de 4 blocos) para que o resultado do chunk independa de onde a janela termina. */
    int requiredMargin() {
        int m = Math.max(Math.max(hw(microR), hw(localR)), Math.max(hw(regionalR), hw(macroR)));
        return m + 2 + SLOPE_ITERS; // cada iteracao do limitador de slope enxerga 1 no a mais
    }

    static SmoothParams of(double strength, double radius, double regional, double macro,
                           double detailKeep, double maxSlope, int terraceStep, double terraceStrength,
                           double terraceTreadWidth) {
        double micro = Math.max(2.0, radius / 4.0);
        double local = Math.max(micro + 2, radius * 2.0 / 3.0);
        double reg = Math.max(local + 4, regional);
        double mac = Math.min(48.0, Math.max(reg + 8, macro));
        return new SmoothParams(micro, local, reg, mac, strength / 0.65, detailKeep,
            TerrainMath.clamp(maxSlope, 0.3, 4.0), TerrainMath.clampInt(terraceStep, 0, 8),
            TerrainMath.clamp01(terraceStrength),
            Math.max(1, terraceStep) / TerrainMath.clamp(terraceTreadWidth, 2.0, 24.0));
    }
}
