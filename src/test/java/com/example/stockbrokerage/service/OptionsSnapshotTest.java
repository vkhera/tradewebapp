package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OptionsSnapshot} predicate methods.
 */
class OptionsSnapshotTest {

    // ── unavailable factory ───────────────────────────────────────────────────

    @Test
    void unavailable_setsDataAvailableFalse() {
        OptionsSnapshot snap = OptionsSnapshot.unavailable("AAPL");
        assertThat(snap.dataAvailable()).isFalse();
        assertThat(snap.symbol()).isEqualTo("AAPL");
    }

    @Test
    void unavailable_allSignalFieldsAreZero() {
        OptionsSnapshot snap = OptionsSnapshot.unavailable("TSLA");
        assertThat(snap.atmImpliedVolatility()).isZero();
        assertThat(snap.putCallRatioOI()).isZero();
        assertThat(snap.maxPain()).isZero();
    }

    // ── isHighIV ──────────────────────────────────────────────────────────────

    @Test
    void isHighIV_returnsTrueWhenIVAtThreshold() {
        OptionsSnapshot snap = new OptionsSnapshot("AAPL", 0.40, 1.0, 150.0, true);
        assertThat(snap.isHighIV()).isTrue();
    }

    @Test
    void isHighIV_returnsTrueWhenIVAboveThreshold() {
        OptionsSnapshot snap = new OptionsSnapshot("AAPL", 0.55, 1.0, 150.0, true);
        assertThat(snap.isHighIV()).isTrue();
    }

    @Test
    void isHighIV_returnsFalseWhenIVBelowThreshold() {
        OptionsSnapshot snap = new OptionsSnapshot("AAPL", 0.39, 1.0, 150.0, true);
        assertThat(snap.isHighIV()).isFalse();
    }

    @Test
    void isHighIV_returnsFalseWhenDataUnavailable() {
        // IV would be high but data flag is off
        OptionsSnapshot snap = new OptionsSnapshot("AAPL", 0.55, 1.0, 150.0, false);
        assertThat(snap.isHighIV()).isFalse();
    }

    // ── isExtremeIV ───────────────────────────────────────────────────────────

    @Test
    void isExtremeIV_returnsTrueWhenIVAt60Pct() {
        OptionsSnapshot snap = new OptionsSnapshot("NVDA", 0.60, 1.0, 200.0, true);
        assertThat(snap.isExtremeIV()).isTrue();
    }

    @Test
    void isExtremeIV_returnsFalseWhenIVJustBelow60Pct() {
        OptionsSnapshot snap = new OptionsSnapshot("NVDA", 0.599, 1.0, 200.0, true);
        assertThat(snap.isExtremeIV()).isFalse();
    }

    @Test
    void isExtremeIV_returnsFalseWhenDataUnavailable() {
        OptionsSnapshot snap = new OptionsSnapshot("NVDA", 0.80, 1.0, 200.0, false);
        assertThat(snap.isExtremeIV()).isFalse();
    }

    // ── isExtremeFear ────────────────────────────────────────────────────────

    @Test
    void isExtremeFear_returnsTrueWhenPCRAbove1_5() {
        OptionsSnapshot snap = new OptionsSnapshot("SPY", 0.20, 1.6, 400.0, true);
        assertThat(snap.isExtremeFear()).isTrue();
    }

    @Test
    void isExtremeFear_returnsFalseWhenPCRExactly1_5() {
        // boundary: > 1.5, not >=
        OptionsSnapshot snap = new OptionsSnapshot("SPY", 0.20, 1.5, 400.0, true);
        assertThat(snap.isExtremeFear()).isFalse();
    }

    @Test
    void isExtremeFear_returnsFalseWhenPCRBelow1_5() {
        OptionsSnapshot snap = new OptionsSnapshot("SPY", 0.20, 1.2, 400.0, true);
        assertThat(snap.isExtremeFear()).isFalse();
    }

    @Test
    void isExtremeFear_returnsFalseWhenDataUnavailable() {
        OptionsSnapshot snap = new OptionsSnapshot("SPY", 0.20, 2.0, 400.0, false);
        assertThat(snap.isExtremeFear()).isFalse();
    }

    // ── isExtremeGreed ───────────────────────────────────────────────────────

    @Test
    void isExtremeGreed_returnsTrueWhenPCRBelow0_7() {
        OptionsSnapshot snap = new OptionsSnapshot("QQQ", 0.20, 0.6, 350.0, true);
        assertThat(snap.isExtremeGreed()).isTrue();
    }

    @Test
    void isExtremeGreed_returnsFalseWhenPCRExactly0_7() {
        // boundary: < 0.7, not <=
        OptionsSnapshot snap = new OptionsSnapshot("QQQ", 0.20, 0.7, 350.0, true);
        assertThat(snap.isExtremeGreed()).isFalse();
    }

    @Test
    void isExtremeGreed_returnsFalseWhenDataUnavailable() {
        OptionsSnapshot snap = new OptionsSnapshot("QQQ", 0.20, 0.5, 350.0, false);
        assertThat(snap.isExtremeGreed()).isFalse();
    }

    // ── combined: high IV + extreme fear ─────────────────────────────────────

    @Test
    void snapshot_canBeHighIVAndExtremeFearSimultaneously() {
        OptionsSnapshot snap = new OptionsSnapshot("TQQQ", 0.65, 1.8, 50.0, true);
        assertThat(snap.isExtremeIV()).isTrue();
        assertThat(snap.isHighIV()).isTrue();
        assertThat(snap.isExtremeFear()).isTrue();
        assertThat(snap.isExtremeGreed()).isFalse();
    }
}
