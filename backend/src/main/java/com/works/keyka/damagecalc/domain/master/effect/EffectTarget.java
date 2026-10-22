package com.works.keyka.damagecalc.domain.master.effect;

import java.util.Locale;
import java.util.Optional;

/**
 * 効果の作用対象。仕様: docs/master-data-spec.md §3
 * 新しい対象が必要になったら、ここに足す(CSVの誤字は読み込み時にこの一覧との照合で検出される)。
 */
public enum EffectTarget {
    OWN_MOVE_POWER,
    OPPONENT_MOVE_POWER,
    OWN_STATUS_A, OWN_STATUS_B, OWN_STATUS_C, OWN_STATUS_D, OWN_STATUS_S,
    OPPONENT_STATUS_A, OPPONENT_STATUS_B, OPPONENT_STATUS_C, OPPONENT_STATUS_D,
    DAMAGE,
    OWN_MOVE_TYPE,
    STAB,
    CRITICAL_RATIO,
    /** 場の天候(どちらの側の特性でも適用される) */
    WEATHER,
    /** 持ち主が攻撃するときだけの天候 */
    OWN_WEATHER,
    TYPE_EFFECTIVE;

    /** DB・CSVでの表記(小文字のスネークケース)。CSVの "STAB" も受け付け、"stab" として保存する。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<EffectTarget> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
