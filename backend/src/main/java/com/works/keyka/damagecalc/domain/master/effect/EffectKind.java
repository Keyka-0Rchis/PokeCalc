package com.works.keyka.damagecalc.domain.master.effect;

import java.util.Locale;
import java.util.Optional;

/** 効果の種類。仕様: docs/master-data-spec.md §2 */
public enum EffectKind {
    /** その段階の値に倍率を掛ける。0は無効。 */
    MULTIPLY,
    /** 値(タイプ・天候・補正値)を置き換える。 */
    REPLACE,
    /** 倍率では表せない処理。params の rule キーでクラスに対応づける。 */
    RULE;

    public static Optional<EffectKind> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
