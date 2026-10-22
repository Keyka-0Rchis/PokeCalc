package com.works.keyka.damagecalc.infrastructure.seed;

import com.works.keyka.damagecalc.infrastructure.seed.SeedData.AbilityRef;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Effect;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Named;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.PokemonForm;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.works.keyka.damagecalc.infrastructure.seed.SqlBatch.row;

/**
 * 検証済みの {@link SeedData} を、DBへ入れるSQLの列にする。何度実行しても同じ結果になる。
 *
 * <ol>
 *   <li>他から参照されない子表(リンク表・効果表・タイプ相性)を空にする</li>
 *   <li>親表を id で upsert する(idを保ったまま、名前や種族値の更新を反映する)</li>
 *   <li>CSVから消えた親表の行を削除する(他の表から参照されていれば、外部キー違反で止まる)</li>
 *   <li>子表を入れ直す</li>
 * </ol>
 */
public final class SeedPlan {

    private SeedPlan() {
    }

    public static List<SqlBatch> build(SeedData d) {
        List<SqlBatch> plan = new ArrayList<>();

        for (String table : List.of("transformation", "pokemon_form_ability", "pokemon_form_type", "move_flag",
                "ability_effect", "item_effect", "type_effectiveness")) {
            plan.add(SqlBatch.plain("clear " + table, "delete from " + table));
        }

        plan.add(SqlBatch.withRows("upsert element_type",
                "insert into element_type (id, name) values (?, ?) "
                        + "on conflict (id) do update set name = excluded.name",
                named(d.types(), false)));
        plan.add(SqlBatch.withRows("upsert move_flag_type",
                "insert into move_flag_type (id, name) values (?, ?) "
                        + "on conflict (id) do update set name = excluded.name",
                named(d.moveFlagTypes(), false)));
        plan.add(SqlBatch.withRows("upsert ability",
                "insert into ability (id, name, name_kana) values (?, ?, ?) "
                        + "on conflict (id) do update set name = excluded.name, name_kana = excluded.name_kana",
                named(d.abilities(), true)));
        plan.add(SqlBatch.withRows("upsert item",
                "insert into item (id, name, name_kana) values (?, ?, ?) "
                        + "on conflict (id) do update set name = excluded.name, name_kana = excluded.name_kana",
                named(d.items(), true)));

        List<List<Object>> moveRows = new ArrayList<>();
        for (SeedData.Move m : d.moves()) {
            moveRows.add(row(m.id(), m.name(), KanaNormalizer.normalize(m.name()), m.typeId(), m.category(),
                    m.power(), m.minHits(), m.maxHits(), m.ruleKey()));
        }
        plan.add(SqlBatch.withRows("upsert move",
                "insert into move (id, name, name_kana, type_id, category, power, min_hits, max_hits, rule_key) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (id) do update set name = excluded.name, name_kana = excluded.name_kana, "
                        + "type_id = excluded.type_id, category = excluded.category, power = excluded.power, "
                        + "min_hits = excluded.min_hits, max_hits = excluded.max_hits, rule_key = excluded.rule_key",
                moveRows));

        List<List<Object>> formRows = new ArrayList<>();
        for (PokemonForm f : d.forms()) {
            formRows.add(row(f.id(), f.dexNo(), f.name(), KanaNormalizer.normalize(f.name()), f.region(),
                    f.variantKey(), f.megaKind(), f.baseH(), f.baseA(), f.baseB(), f.baseC(), f.baseD(), f.baseS()));
        }
        plan.add(SqlBatch.withRows("upsert pokemon_form",
                "insert into pokemon_form (id, dex_no, name, name_kana, region, variant_key, mega_kind, "
                        + "base_h, base_a, base_b, base_c, base_d, base_s) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (id) do update set dex_no = excluded.dex_no, name = excluded.name, "
                        + "name_kana = excluded.name_kana, region = excluded.region, "
                        + "variant_key = excluded.variant_key, mega_kind = excluded.mega_kind, "
                        + "base_h = excluded.base_h, base_a = excluded.base_a, base_b = excluded.base_b, "
                        + "base_c = excluded.base_c, base_d = excluded.base_d, base_s = excluded.base_s",
                formRows));

        // CSVから消えた行の削除(子 → 親の順)
        plan.add(deleteMissing("pokemon_form", d.forms().stream().map(PokemonForm::id).toList()));
        plan.add(deleteMissing("move", d.moves().stream().map(SeedData.Move::id).toList()));
        plan.add(deleteMissing("item", d.items().stream().map(Named::id).toList()));
        plan.add(deleteMissing("ability", d.abilities().stream().map(Named::id).toList()));
        plan.add(deleteMissing("move_flag_type", d.moveFlagTypes().stream().map(Named::id).toList()));
        plan.add(deleteMissing("element_type", d.types().stream().map(Named::id).toList()));

        List<List<Object>> chartRows = new ArrayList<>();
        for (SeedData.TypeEffectiveness e : d.typeChart()) {
            chartRows.add(row(e.attackTypeId(), e.defendTypeId(), e.multiplier()));
        }
        plan.add(SqlBatch.withRows("insert type_effectiveness",
                "insert into type_effectiveness (attack_type_id, defend_type_id, multiplier) values (?, ?, ?)",
                chartRows));

        List<List<Object>> formTypeRows = new ArrayList<>();
        List<List<Object>> formAbilityRows = new ArrayList<>();
        for (PokemonForm f : d.forms()) {
            for (int i = 0; i < f.typeIds().size(); i++) {
                formTypeRows.add(row(f.id(), i + 1, f.typeIds().get(i)));
            }
            for (AbilityRef a : f.abilities()) {
                formAbilityRows.add(row(f.id(), a.abilityId(), a.hidden()));
            }
        }
        plan.add(SqlBatch.withRows("insert pokemon_form_type",
                "insert into pokemon_form_type (form_id, slot, type_id) values (?, ?, ?)", formTypeRows));
        plan.add(SqlBatch.withRows("insert pokemon_form_ability",
                "insert into pokemon_form_ability (form_id, ability_id, is_hidden) values (?, ?, ?)", formAbilityRows));

        List<List<Object>> transformationRows = new ArrayList<>();
        for (SeedData.Transformation t : d.transformations()) {
            transformationRows.add(row(t.fromFormId(), t.toFormId(), t.kind()));
        }
        plan.add(SqlBatch.withRows("insert transformation",
                "insert into transformation (from_form_id, to_form_id, kind) values (?, ?, ?)", transformationRows));

        List<List<Object>> moveFlagRows = new ArrayList<>();
        for (SeedData.MoveFlag f : d.moveFlags()) {
            moveFlagRows.add(row(f.moveId(), f.flagId()));
        }
        plan.add(SqlBatch.withRows("insert move_flag",
                "insert into move_flag (move_id, flag_id) values (?, ?)", moveFlagRows));

        plan.add(effects("ability_effect", "ability_id", d.abilityEffects()));
        plan.add(effects("item_effect", "item_id", d.itemEffects()));
        return plan;
    }

    private static List<List<Object>> named(List<Named> rows, boolean withKana) {
        List<List<Object>> result = new ArrayList<>();
        for (Named n : rows) {
            result.add(withKana ? row(n.id(), n.name(), KanaNormalizer.normalize(n.name())) : row(n.id(), n.name()));
        }
        return result;
    }

    private static SqlBatch effects(String table, String ownerColumn, List<Effect> effects) {
        List<List<Object>> rows = new ArrayList<>();
        for (Effect e : effects) {
            rows.add(row(e.ownerId(), e.seq(), e.target(), e.kind(), e.multiplier4096(), e.conditions(), e.phase(),
                    e.paramsJson()));
        }
        return SqlBatch.withRows("insert " + table,
                "insert into " + table + " (" + ownerColumn + ", seq, effect_target, kind, multiplier_4096, "
                        + "conditions, phase, params) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))",
                rows);
    }

    /** ids は検証済みの整数なので、リテラルとして埋め込んでよい。ids が空のときは何も消さない(全削除を避ける)。 */
    private static SqlBatch deleteMissing(String table, List<Integer> ids) {
        if (ids.isEmpty()) {
            return SqlBatch.plain("skip delete " + table + " (no rows in CSV)", "select 1");
        }
        String list = ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return SqlBatch.plain("delete removed " + table, "delete from " + table + " where id not in (" + list + ")");
    }
}
