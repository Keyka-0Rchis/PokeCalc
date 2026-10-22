package com.works.keyka.damagecalc.infrastructure.seed;

import java.math.BigDecimal;
import java.util.List;

/** 検証済みのシードデータ。DBに入れる直前の形(MasterSeedParser が作る)。 */
public record SeedData(
        List<Named> types,
        List<TypeEffectiveness> typeChart,
        List<Named> abilities,
        List<Named> items,
        List<Named> moveFlagTypes,
        List<Move> moves,
        List<MoveFlag> moveFlags,
        List<Effect> abilityEffects,
        List<Effect> itemEffects,
        List<PokemonForm> forms,
        List<Transformation> transformations) {

    /** id と名前だけを持つマスタ(タイプ・特性・持ち物・技フラグの種類)。 */
    public record Named(int id, String name) {
    }

    public record TypeEffectiveness(int attackTypeId, int defendTypeId, BigDecimal multiplier) {
    }

    /** power は変動・威力なしのとき null。category は PHYSICAL / SPECIAL / STATUS。 */
    public record Move(int id, String name, int typeId, String category, Integer power,
                       int minHits, int maxHits, String ruleKey) {
    }

    public record MoveFlag(int moveId, int flagId) {
    }

    /**
     * 特性または持ち物の効果1件。seq はCSVの行順(1始まり)。
     * target / multiplier / conditions / phase / paramsJson は無いとき null。
     */
    public record Effect(int ownerId, int seq, String target, String kind, Integer multiplier4096,
                         String conditions, String phase, String paramsJson) {
    }

    /** typeIds は 1件(単タイプ)または2件。abilities は通常特性→夢特性の順。 */
    public record PokemonForm(int id, int dexNo, String name, String region, String variantKey, String megaKind,
                              int baseH, int baseA, int baseB, int baseC, int baseD, int baseS,
                              List<Integer> typeIds, List<AbilityRef> abilities) {
    }

    public record AbilityRef(int abilityId, boolean hidden) {
    }

    public record Transformation(int fromFormId, int toFormId, String kind) {
    }
}
