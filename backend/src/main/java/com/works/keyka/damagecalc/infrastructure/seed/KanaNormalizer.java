package com.works.keyka.damagecalc.infrastructure.seed;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 前方一致サジェスト用の正規化。NFKC → 小文字 → カタカナをひらがなへ。
 * フロントエンドも同じ規則で入力を正規化する必要がある(共通テストケースで揃える)。
 */
public final class KanaNormalizer {

    private KanaNormalizer() {
    }

    public static String normalize(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'ァ' && c <= 'ヶ') || c == 'ヽ' || c == 'ヾ') {
                sb.append((char) (c - 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
