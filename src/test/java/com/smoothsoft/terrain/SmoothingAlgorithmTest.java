package com.smoothsoft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mundo A (terreno original) vs mundo B (SOFT_WORLD), mesma seed sintetica. Rode: ./gradlew test */
class SmoothingAlgorithmTest {
    private final SmoothParams soft = SmoothParams.of(0.90, 16, 32, 46, 0.12, 0.7, 1, 0.85, 5.0);
    private final SmoothParams subtle = SmoothParams.of(0.30, 8, 16, 28, 0.45, 1.4, 0, 0.0, 5.0);

    @Test
    void noSeamsBetweenChunks() {
        assertEquals(0.0, AlgorithmChecks.seamError(42, soft, soft.requiredMargin()), 1e-9);
    }

    @Test
    void softWorldIsVisiblySmootherAndWalkable() {
        for (long seed : new long[] {1, 42, 777}) {
            AlgorithmChecks.Report r = AlgorithmChecks.compare(seed, soft, 16, 24);
            assertTrue(1 - r.roughFinal() / r.roughOrig() > 0.40, "rugosidade quase igual");
            assertTrue(r.steps2Final() < 3.5, "degraus >=2 blocos ainda frequentes: " + r.steps2Final());
            assertTrue(r.steps3Final() < 1.0, "degraus >=3 blocos ainda frequentes: " + r.steps3Final());
            assertTrue(r.steps3Final() < r.steps3Orig() * 0.5, "degraus nao diminuiram o bastante");
            assertTrue(r.pitsFinal() <= r.pitsOrig(), "pocos aumentaram");
            assertTrue(r.treadFinal() > r.treadOrig() * 1.25 && r.treadFinal() >= 2.6, "terracos pouco visiveis: " + r.treadOrig() + " -> " + r.treadFinal());
            assertTrue(Math.abs(r.meanFinal() - r.meanOrig()) < 1.0, "altura media nao preservada");
            assertTrue(r.maxFinal() > r.maxOrig() * 0.88, "montanhas ficaram baixas demais");
            assertTrue(r.maxAbsChange() <= 24, "mudanca acima do limite");
        }
    }

    @Test
    void modesAreOrderedByStrength() {
        double a = AlgorithmChecks.compare(42, subtle, 16, 24).meanAbsChange();
        double b = AlgorithmChecks.compare(42, soft, 16, 24).meanAbsChange();
        assertTrue(b > a * 3, "SOFT_WORLD deveria alterar bem mais que SUBTLE");
    }
}
