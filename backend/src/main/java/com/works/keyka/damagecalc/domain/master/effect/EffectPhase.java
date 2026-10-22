package com.works.keyka.damagecalc.domain.master.effect;

import java.util.Locale;
import java.util.Optional;

/**
 * 効果の適用段階。仕様: docs/master-data-spec.md §4
 * 天候の確定 → POWER_START → POWER_FINAL → STATUS_FINAL → DAMAGE_FINAL の順に適用する。
 */
public enum EffectPhase {
    POWER_START,
    POWER_FINAL,
    STATUS_FINAL,
    DAMAGE_FINAL;

    /** DB・CSVでの表記(小文字のスネークケース)。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<EffectPhase> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
