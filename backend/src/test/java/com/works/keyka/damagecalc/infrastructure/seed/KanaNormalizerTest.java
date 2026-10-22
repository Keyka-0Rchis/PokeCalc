package com.works.keyka.damagecalc.infrastructure.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KanaNormalizerTest {

    @Test
    void カタカナはひらがなになり長音は残る() {
        assertEquals("りざーどん", KanaNormalizer.normalize("リザードン"));
    }

    @Test
    void 英字は小文字になる() {
        assertEquals("めがりざーどんx", KanaNormalizer.normalize("メガリザードンX"));
        assertEquals("arしすてむ", KanaNormalizer.normalize("ARシステム"));
    }

    @Test
    void 全角英数と半角カナは統一される() {
        assertEquals("arしすてむ", KanaNormalizer.normalize("ＡＲシステム"));
        assertEquals("ぴかちゅう", KanaNormalizer.normalize("ﾋﾟｶﾁｭｳ"));
    }

    @Test
    void 濁点つきのヴも変換される() {
        assertEquals("ゔ", KanaNormalizer.normalize("ヴ"));
    }

    @Test
    void ひらがなはそのまま() {
        assertEquals("もうか", KanaNormalizer.normalize("もうか"));
    }
}
