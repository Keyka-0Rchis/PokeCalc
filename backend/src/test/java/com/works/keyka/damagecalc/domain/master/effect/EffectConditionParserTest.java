package com.works.keyka.damagecalc.domain.master.effect;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectConditionParserTest {

    private static final EffectConditionParser.Lookup LOOKUP = new EffectConditionParser.Lookup() {
        @Override
        public boolean typeExists(int id) {
            return id >= 1 && id <= 19;
        }

        @Override
        public boolean abilityExists(int id) {
            return id == 186 || id == 187 || id == 188;
        }

        @Override
        public boolean moveFlagExists(int id) {
            return id >= 1 && id <= 10;
        }
    };

    private static EffectConditionParser.Result parse(String raw, EffectTarget target) {
        return EffectConditionParser.parse(raw, target, LOOKUP);
    }

    @Test
    void 空の条件は条件なし() {
        EffectConditionParser.Result r = parse("", EffectTarget.DAMAGE);
        assertTrue(r.errors().isEmpty());
        assertTrue(r.atoms().isEmpty());
        assertNull(r.conditionsText());
    }

    @Test
    void セミコロン区切りは複数条件になり元の形に戻せる() {
        EffectConditionParser.Result r = parse("own_hp_ratio<=1/3;type_id:2", EffectTarget.OWN_STATUS_A);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(2, r.atoms().size());
        assertEquals("own_hp_ratio<=1/3;type_id:2", r.conditionsText());
    }

    @Test
    void fromとtoはパラメータとして分けられる() {
        EffectConditionParser.Result r = parse("from:1;to:6", EffectTarget.OWN_MOVE_TYPE);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertTrue(r.atoms().isEmpty());
        assertEquals(Map.of("from", "1", "to", "6"), r.params());
    }

    @Test
    void 天候の置き換えにはtoが使えるが技タイプの行では天候名は使えない() {
        assertTrue(parse("to:sunny", EffectTarget.OWN_WEATHER).errors().isEmpty());
        assertTrue(parse("to:none", EffectTarget.WEATHER).errors().isEmpty());
        assertFalse(parse("to:sunny", EffectTarget.OWN_MOVE_TYPE).errors().isEmpty());
        assertFalse(parse("to:6", EffectTarget.DAMAGE).errors().isEmpty());
    }

    @Test
    void 区切りの打ち間違いは検出される() {
        // ';' のつもりで ':' を書いた例
        assertFalse(parse("own_hp_ratio<=1/3:type_id:2", EffectTarget.OWN_STATUS_A).errors().isEmpty());
    }

    @Test
    void 相手の特性は等しいと等しくないの両方を書ける() {
        assertTrue(parse("opponent_ability_id!=188;type_id:16", EffectTarget.OWN_MOVE_POWER).errors().isEmpty());
        assertTrue(parse("opponent_ability_id:186", EffectTarget.OWN_MOVE_POWER).errors().isEmpty());
        assertFalse(parse("opponent_ability_id:9999", EffectTarget.OWN_MOVE_POWER).errors().isEmpty());
    }

    @Test
    void 急所は単独で書ける() {
        EffectConditionParser.Result r = parse("critical", null);
        assertTrue(r.errors().isEmpty());
        assertEquals("critical", r.conditionsText());
    }

    @Test
    void 未知のキーや語は誤字として報告される() {
        assertFalse(parse("apply_flag:on", EffectTarget.OWN_STATUS_S).errors().isEmpty());
        assertFalse(parse("criticall", null).errors().isEmpty());
        assertFalse(parse("type_id:16;;weather:rain", EffectTarget.DAMAGE).errors().isEmpty());
        assertFalse(parse("field:electoric", EffectTarget.OWN_STATUS_S).errors().isEmpty());
    }

    @Test
    void 比較は数値と分数を受け付ける() {
        assertTrue(parse("type_effectiveness>=2", EffectTarget.DAMAGE).errors().isEmpty());
        assertTrue(parse("type_effectiveness<=1/2", EffectTarget.DAMAGE).errors().isEmpty());
        assertTrue(parse("power<=60", EffectTarget.OWN_MOVE_POWER).errors().isEmpty());
        assertFalse(parse("power<=sixty", EffectTarget.OWN_MOVE_POWER).errors().isEmpty());
        assertFalse(parse("own_hp_ratio<=1/0", EffectTarget.OWN_STATUS_A).errors().isEmpty());
    }
}
