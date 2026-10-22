package com.works.keyka.damagecalc.infrastructure.seed;

import com.works.keyka.damagecalc.domain.master.effect.EffectConditionParser;
import com.works.keyka.damagecalc.domain.master.effect.EffectKind;
import com.works.keyka.damagecalc.domain.master.effect.EffectPhase;
import com.works.keyka.damagecalc.domain.master.effect.EffectTarget;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.AbilityRef;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Effect;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Move;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.MoveFlag;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Named;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.PokemonForm;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.Transformation;
import com.works.keyka.damagecalc.infrastructure.seed.SeedData.TypeEffectiveness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * シードCSVを読み込み、検証して {@link SeedData} にする。
 * 問題は行番号つきで {@link SeedProblems} に集める(最初の1件で止めない)。
 * エラーが1件でもあれば、返す SeedData は使ってはいけない。
 */
public final class MasterSeedParser {

    /** タイプ「ー」(タイプなし)のid。type.csv と一致させる。攻撃タイプにはならず、防御側としては常に等倍。 */
    public static final int NO_TYPE_ID = 19;

    /** 読み込むファイル。順序は依存関係(参照される側が先)。 */
    public static final List<String> FILES = List.of(
            "type.csv", "type_effective.csv", "ability.csv", "item.csv", "master_move_flag.csv",
            "move.csv", "move_flag.csv", "ability_effect.csv", "item_effect.csv", "pokemon.csv");

    public record Result(SeedData data, SeedProblems problems) {
    }

    public static Result parse(SeedSource source) {
        return new MasterSeedParser(source).run();
    }

    private static final Pattern RULE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9_]*$");
    private static final Set<String> CATEGORIES = Set.of("PHYSICAL", "SPECIAL", "STATUS");
    private static final Set<String> MULTIPLIERS = Set.of("0", "0.5", "1", "2");
    private static final Set<EffectTarget> TO_TARGETS =
            Set.of(EffectTarget.OWN_MOVE_TYPE, EffectTarget.WEATHER, EffectTarget.OWN_WEATHER);
    private static final Set<EffectTarget> VALUE_TARGETS = Set.of(EffectTarget.STAB, EffectTarget.CRITICAL_RATIO);

    private final SeedSource source;
    private final SeedProblems problems = new SeedProblems();

    private final Map<Integer, String> types = new LinkedHashMap<>();
    private final Map<Integer, String> abilities = new LinkedHashMap<>();
    private final Map<Integer, String> items = new LinkedHashMap<>();
    private final Map<Integer, String> flagTypes = new LinkedHashMap<>();
    private final Map<Integer, String> moves = new LinkedHashMap<>();

    private final EffectConditionParser.Lookup lookup = new EffectConditionParser.Lookup() {
        @Override
        public boolean typeExists(int id) {
            return types.containsKey(id);
        }

        @Override
        public boolean abilityExists(int id) {
            return abilities.containsKey(id);
        }

        @Override
        public boolean moveFlagExists(int id) {
            return flagTypes.containsKey(id);
        }
    };

    private MasterSeedParser(SeedSource source) {
        this.source = source;
    }

    private Result run() {
        List<Named> typeRows = parseNamed("type.csv", "type_id", types);
        List<TypeEffectiveness> chart = parseTypeChart();
        List<Named> abilityRows = parseNamed("ability.csv", "id", abilities);
        List<Named> itemRows = parseNamed("item.csv", "id", items);
        List<Named> flagTypeRows = parseNamed("master_move_flag.csv", "id", flagTypes);
        List<Move> moveRows = parseMoves();
        List<MoveFlag> moveFlagRows = parseMoveFlags();
        List<Effect> abilityEffects = parseEffects("ability_effect.csv", "ability_id", abilities);
        List<Effect> itemEffects = parseEffects("item_effect.csv", "item_id", items);
        List<Transformation> transformations = new ArrayList<>();
        List<PokemonForm> forms = parsePokemon(transformations);

        SeedData data = new SeedData(typeRows, chart, abilityRows, itemRows, flagTypeRows, moveRows,
                moveFlagRows, abilityEffects, itemEffects, forms, transformations);
        return new Result(data, problems);
    }

    // ------------------------------------------------------------------ 共通

    private CsvTable load(String file, String... columns) {
        Optional<byte[]> bytes = source.read(file);
        if (bytes.isEmpty()) {
            problems.error(file, 0, "ファイルが見つかりません");
            return null;
        }
        CsvTable table = CsvTable.parse(file, bytes.get(), problems);
        if (table == null || !table.requireColumns(problems, columns)) {
            return null;
        }
        if (table.rows.isEmpty()) {
            problems.error(file, 0, "データ行がありません");
        }
        return table;
    }

    /** 整数列。空・非整数・範囲外はエラーにして null を返す。 */
    private Integer integer(CsvTable t, CsvTable.Row r, String col, int min, int max) {
        String v = r.get(col);
        if (v.isEmpty()) {
            problems.error(t.file, r.line(), col + " が空です");
            return null;
        }
        return parseInteger(t, r, col, v, min, max);
    }

    /** 整数列(空なら null、エラーなし)。 */
    private Integer optionalInteger(CsvTable t, CsvTable.Row r, String col, int min, int max) {
        String v = r.get(col);
        return v.isEmpty() ? null : parseInteger(t, r, col, v, min, max);
    }

    private Integer parseInteger(CsvTable t, CsvTable.Row r, String col, String v, int min, int max) {
        long n;
        try {
            n = Long.parseLong(v);
        } catch (NumberFormatException e) {
            problems.error(t.file, r.line(), col + " は整数で指定してください: \"" + v + "\"");
            return null;
        }
        if (n < min || n > max) {
            problems.error(t.file, r.line(), col + " が範囲外です(" + min + "〜" + max + "): " + n);
            return null;
        }
        return (int) n;
    }

    private String text(CsvTable t, CsvTable.Row r, String col) {
        String v = r.get(col);
        if (v.isEmpty()) {
            problems.error(t.file, r.line(), col + " が空です");
            return null;
        }
        // 表計算ソフトのセル内改行は、サジェスト表示と前方一致を壊すので取り込まない。
        // (前後の空白は CsvTable が読み込み時に除去するので、ここで見るのはセルの内側の制御文字)
        if (col.equals("name") && v.codePoints().anyMatch(Character::isISOControl)) {
            problems.error(t.file, r.line(), col + " に改行・タブなどの制御文字が含まれています: \""
                    + visible(v) + "\"");
            return null;
        }
        return v;
    }

    /** エラーメッセージ用に、改行・タブを目に見える形にする。 */
    private static String visible(String v) {
        return v.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    // ------------------------------------------------------------------ id + name だけのマスタ

    private List<Named> parseNamed(String file, String idCol, Map<Integer, String> out) {
        List<Named> result = new ArrayList<>();
        CsvTable t = load(file, idCol, "name");
        if (t == null) {
            return result;
        }
        Map<String, Integer> nameToId = new HashMap<>();
        for (CsvTable.Row r : t.rows) {
            Integer id = integer(t, r, idCol, 1, Integer.MAX_VALUE);
            String name = text(t, r, "name");
            if (id == null || name == null) {
                continue;
            }
            if (out.containsKey(id)) {
                problems.error(file, r.line(), idCol + " " + id + " が重複しています");
                continue;
            }
            // 型・技フラグの名前はDBのunique制約があるので、ここで重複を検出する
            if (file.equals("type.csv") || file.equals("master_move_flag.csv")) {
                Integer prev = nameToId.put(name, id);
                if (prev != null) {
                    problems.error(file, r.line(), "name \"" + name + "\" が id " + prev + " と重複しています");
                    continue;
                }
            }
            out.put(id, name);
            result.add(new Named(id, name));
        }
        return result;
    }

    // ------------------------------------------------------------------ タイプ相性

    private List<TypeEffectiveness> parseTypeChart() {
        List<TypeEffectiveness> result = new ArrayList<>();
        String file = "type_effective.csv";
        CsvTable t = load(file, "type_attack_id", "type_defence_id", "effective");
        if (t == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (CsvTable.Row r : t.rows) {
            Integer att = integer(t, r, "type_attack_id", 1, Integer.MAX_VALUE);
            Integer def = integer(t, r, "type_defence_id", 1, Integer.MAX_VALUE);
            String eff = text(t, r, "effective");
            if (att == null || def == null || eff == null) {
                continue;
            }
            boolean ok = true;
            if (!types.containsKey(att)) {
                problems.error(file, r.line(), "type_attack_id " + att + " は type.csv にありません");
                ok = false;
            }
            if (!types.containsKey(def)) {
                problems.error(file, r.line(), "type_defence_id " + def + " は type.csv にありません");
                ok = false;
            }
            if (!MULTIPLIERS.contains(eff)) {
                problems.error(file, r.line(), "effective は 0 / 0.5 / 1 / 2 のいずれかです: \"" + eff + "\"");
                ok = false;
            }
            if (att == NO_TYPE_ID) {
                problems.error(file, r.line(), "タイプ「ー」(id " + NO_TYPE_ID + ")は攻撃側になれません");
                ok = false;
            }
            if (def == NO_TYPE_ID && !eff.equals("1")) {
                problems.error(file, r.line(), "防御側が「ー」(タイプなし)のときは常に等倍(1)です: " + eff);
                ok = false;
            }
            if (!seen.add(att + "/" + def)) {
                problems.error(file, r.line(), "組 (" + att + ", " + def + ") が重複しています");
                ok = false;
            }
            if (ok) {
                result.add(new TypeEffectiveness(att, def, new BigDecimal(eff)));
            }
        }
        // 全組み合わせが揃っているか(攻撃側は「ー」以外の全タイプ、防御側は全タイプ)
        List<String> missing = new ArrayList<>();
        for (int a : types.keySet()) {
            if (a == NO_TYPE_ID) {
                continue;
            }
            for (int d : types.keySet()) {
                if (!seen.contains(a + "/" + d)) {
                    missing.add("(" + a + "," + d + ")");
                }
            }
        }
        if (!missing.isEmpty()) {
            problems.error(file, 0, "タイプ相性の組が足りません: "
                    + String.join(" ", missing.subList(0, Math.min(10, missing.size())))
                    + (missing.size() > 10 ? " ほか" + (missing.size() - 10) + "件" : ""));
        }
        return result;
    }

    // ------------------------------------------------------------------ 技

    private List<Move> parseMoves() {
        List<Move> result = new ArrayList<>();
        String file = "move.csv";
        CsvTable t = load(file, "id", "name", "type_id", "power", "min_hit", "max_hit", "category", "rule");
        if (t == null) {
            return result;
        }
        for (CsvTable.Row r : t.rows) {
            Integer id = integer(t, r, "id", 1, Integer.MAX_VALUE);
            String name = text(t, r, "name");
            Integer typeId = integer(t, r, "type_id", 1, Integer.MAX_VALUE);
            Integer power = optionalInteger(t, r, "power", 0, 999);
            Integer minHit = integer(t, r, "min_hit", 1, 10);
            Integer maxHit = integer(t, r, "max_hit", 1, 10);
            String category = r.get("category").toUpperCase(Locale.ROOT);
            String rule = r.get("rule");
            boolean ok = id != null && name != null && typeId != null && minHit != null && maxHit != null;
            if (id != null && moves.containsKey(id)) {
                problems.error(file, r.line(), "id " + id + " が重複しています");
                ok = false;
            }
            if (typeId != null && (!types.containsKey(typeId) || typeId == NO_TYPE_ID)) {
                problems.error(file, r.line(), "type_id " + typeId + " は技のタイプとして使えません");
                ok = false;
            }
            if (!CATEGORIES.contains(category)) {
                problems.error(file, r.line(), "category は physical / special / status のいずれかです: \"" + r.get("category") + "\"");
                ok = false;
            }
            if (minHit != null && maxHit != null && maxHit < minHit) {
                problems.error(file, r.line(), "max_hit が min_hit より小さいです");
                ok = false;
            }
            if (!rule.isEmpty() && !RULE_KEY.matcher(rule).matches()) {
                problems.error(file, r.line(), "rule は大文字英字とアンダースコアで指定してください: \"" + rule + "\"");
                ok = false;
            }
            if (ok) {
                moves.put(id, name);
                result.add(new Move(id, name, typeId, category, power, minHit, maxHit, rule.isEmpty() ? null : rule));
            }
        }
        return result;
    }

    private List<MoveFlag> parseMoveFlags() {
        List<MoveFlag> result = new ArrayList<>();
        String file = "move_flag.csv";
        CsvTable t = load(file, "id?", "move_id", "flag_id");
        if (t == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (CsvTable.Row r : t.rows) {
            Integer moveId = integer(t, r, "move_id", 1, Integer.MAX_VALUE);
            Integer flagId = integer(t, r, "flag_id", 1, Integer.MAX_VALUE);
            if (moveId == null || flagId == null) {
                continue;
            }
            boolean ok = true;
            if (!moves.containsKey(moveId)) {
                problems.error(file, r.line(), "move_id " + moveId + " は move.csv にありません");
                ok = false;
            }
            if (!flagTypes.containsKey(flagId)) {
                problems.error(file, r.line(), "flag_id " + flagId + " は master_move_flag.csv にありません");
                ok = false;
            }
            if (!seen.add(moveId + "/" + flagId)) {
                problems.error(file, r.line(), "技 " + moveId + " にフラグ " + flagId + " が重複して指定されています");
                ok = false;
            }
            if (ok) {
                result.add(new MoveFlag(moveId, flagId));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 効果(特性・持ち物)

    private List<Effect> parseEffects(String file, String ownerCol, Map<Integer, String> owners) {
        List<Effect> result = new ArrayList<>();
        // id 列は読み飛ばす(適用順はCSVの行順で決まり、他から参照もされない)
        CsvTable t = load(file, "id?", ownerCol, "effect_target", "kind", "multiplier_4096", "condition", "phase");
        if (t == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        int seq = 0;
        for (CsvTable.Row r : t.rows) {
            seq++;
            Integer ownerId = integer(t, r, ownerCol, 1, Integer.MAX_VALUE);
            if (ownerId != null && !owners.containsKey(ownerId)) {
                problems.error(file, r.line(), ownerCol + " " + ownerId + " は対応するCSVにありません");
                ownerId = null;
            }

            String targetText = r.get("effect_target");
            EffectTarget target = null;
            boolean ok = ownerId != null;
            if (!targetText.isEmpty()) {
                target = EffectTarget.parse(targetText).orElse(null);
                if (target == null) {
                    problems.error(file, r.line(), "未知の effect_target です: \"" + targetText + "\"");
                    ok = false;
                }
            }
            EffectKind kind = EffectKind.parse(r.get("kind")).orElse(null);
            if (kind == null) {
                problems.error(file, r.line(), "kind は MULTIPLY / REPLACE / RULE のいずれかです: \"" + r.get("kind") + "\"");
                ok = false;
            }
            Integer multiplier = optionalInteger(t, r, "multiplier_4096", 0, 65536);
            if (!r.get("multiplier_4096").isEmpty() && multiplier == null) {
                ok = false;
            }
            EffectPhase phase = null;
            if (!r.get("phase").isEmpty()) {
                phase = EffectPhase.parse(r.get("phase")).orElse(null);
                if (phase == null) {
                    problems.error(file, r.line(), "未知の phase です: \"" + r.get("phase")
                            + "\"(power_start / power_final / status_final / damage_final)");
                    ok = false;
                }
            }
            EffectConditionParser.Result cond = EffectConditionParser.parse(r.get("condition"), target, lookup);
            for (String e : cond.errors()) {
                problems.error(file, r.line(), e);
                ok = false;
            }

            if (kind != null && ok) {
                ok = checkKindRules(file, r.line(), kind, target, multiplier, phase, cond.params());
            }
            if (!ok) {
                continue;
            }
            String paramsJson = toJson(cond.params());
            Effect effect = new Effect(ownerId, seq, target == null ? null : target.code(), kind.name(), multiplier,
                    cond.conditionsText(), phase == null ? null : phase.code(), paramsJson);
            String contentKey = String.join("|", String.valueOf(ownerId), String.valueOf(effect.target()), effect.kind(),
                    String.valueOf(multiplier), String.valueOf(effect.conditions()), String.valueOf(effect.phase()),
                    String.valueOf(paramsJson));
            if (!seen.add(contentKey)) {
                problems.warn(file, r.line(), "同じ内容の効果が重複しています(" + ownerCol + " " + ownerId + ")");
            }
            result.add(effect);
        }
        return result;
    }

    /** kind ごとの必須項目・禁止項目。問題がなければ true。 */
    private boolean checkKindRules(String file, int line, EffectKind kind, EffectTarget target, Integer multiplier,
                                   EffectPhase phase, Map<String, String> params) {
        boolean ok = true;
        switch (kind) {
            case MULTIPLY -> {
                if (target == null) {
                    problems.error(file, line, "kind=MULTIPLY には effect_target が必要です");
                    ok = false;
                }
                if (multiplier == null) {
                    problems.error(file, line, "kind=MULTIPLY には multiplier_4096 が必要です");
                    ok = false;
                }
                if (phase == null) {
                    problems.error(file, line, "kind=MULTIPLY には phase が必要です");
                    ok = false;
                }
                if (!params.isEmpty()) {
                    problems.error(file, line, "kind=MULTIPLY では from: / to: / rule: は使えません: " + params.keySet());
                    ok = false;
                }
            }
            case REPLACE -> {
                if (target == null) {
                    problems.error(file, line, "kind=REPLACE には effect_target が必要です");
                    ok = false;
                } else if (TO_TARGETS.contains(target)) {
                    if (!params.containsKey("to")) {
                        problems.error(file, line, "kind=REPLACE で " + target.code() + " を置き換えるには to: が必要です");
                        ok = false;
                    }
                    if (multiplier != null) {
                        problems.error(file, line, target.code() + " の置き換えに multiplier_4096 は使いません(to: で指定)");
                        ok = false;
                    }
                } else if (VALUE_TARGETS.contains(target)) {
                    if (multiplier == null) {
                        problems.error(file, line, target.code() + " の置き換えには multiplier_4096(置き換え後の値)が必要です");
                        ok = false;
                    }
                } else {
                    problems.error(file, line, "effect_target " + target.code() + " は kind=REPLACE に対応していません");
                    ok = false;
                }
            }
            case RULE -> {
                if (!params.containsKey("rule")) {
                    problems.error(file, line, "kind=RULE には rule:KEY が必要です");
                    ok = false;
                }
            }
        }
        return ok;
    }

    private static String toJson(Map<String, String> params) {
        if (params.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            // キーも値も、パーサが [a-zA-Z0-9_] だけに制限しているのでエスケープは不要
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------ ポケモン

    private List<PokemonForm> parsePokemon(List<Transformation> transformations) {
        List<PokemonForm> result = new ArrayList<>();
        String file = "pokemon.csv";
        String[] abilityCols = {"ability1", "ability2", "ability3"};
        String dreamCol = "ability_dream";
        CsvTable t = load(file, "id", "dex_no", "mega", "region", "form", "name", "type1", "type2",
                "ability1", "ability2", "ability3", "ability_dream",
                "base_h", "base_a", "base_b", "base_c", "base_d", "base_s");
        if (t == null) {
            return result;
        }
        Set<Integer> ids = new HashSet<>();
        Map<String, Integer> naturalKeys = new HashMap<>();
        Set<Integer> usedAbilities = new HashSet<>();
        for (CsvTable.Row r : t.rows) {
            Integer id = integer(t, r, "id", 1, Integer.MAX_VALUE);
            Integer dexNo = integer(t, r, "dex_no", 1, 100000);
            String name = text(t, r, "name");
            Integer type1 = integer(t, r, "type1", 1, Integer.MAX_VALUE);
            Integer type2 = integer(t, r, "type2", 1, Integer.MAX_VALUE);
            Integer h = integer(t, r, "base_h", 1, 255);
            Integer a = integer(t, r, "base_a", 1, 255);
            Integer b = integer(t, r, "base_b", 1, 255);
            Integer c = integer(t, r, "base_c", 1, 255);
            Integer d = integer(t, r, "base_d", 1, 255);
            Integer s = integer(t, r, "base_s", 1, 255);
            boolean ok = id != null && dexNo != null && name != null && type1 != null && type2 != null
                    && h != null && a != null && b != null && c != null && d != null && s != null;

            String mega = r.get("mega");
            String region = r.get("region");
            String variant = r.get("form");
            for (String[] kv : new String[][]{{"mega", mega}, {"region", region}, {"form", variant}}) {
                if (!SLUG.matcher(kv[1]).matches()) {
                    problems.error(file, r.line(), kv[0] + " は小文字英数字とアンダースコアで指定してください: \"" + kv[1] + "\"");
                    ok = false;
                }
            }
            if (id != null && !ids.add(id)) {
                problems.error(file, r.line(), "id " + id + " が重複しています");
                ok = false;
            }
            if (type1 != null && (!types.containsKey(type1) || type1 == NO_TYPE_ID)) {
                problems.error(file, r.line(), "type1 " + type1 + " は使えません(未登録、または「ー」)");
                ok = false;
            }
            if (type2 != null && !types.containsKey(type2)) {
                problems.error(file, r.line(), "type2 " + type2 + " は type.csv にありません");
                ok = false;
            }
            if (type1 != null && type2 != null && type1.equals(type2)) {
                problems.warn(file, r.line(), "type1 と type2 が同じです(" + type1 + ")。単タイプなら type2 は " + NO_TYPE_ID + " にしてください");
            }
            if (ok) {
                String key = dexNo + "|" + region + "|" + variant + "|" + mega;
                Integer prev = naturalKeys.put(key, id);
                if (prev != null) {
                    problems.error(file, r.line(), "(dex_no=" + dexNo + ", region=\"" + region + "\", form=\"" + variant
                            + "\", mega=\"" + mega + "\") が id " + prev + " と重複しています。form 列などで区別してください");
                    ok = false;
                }
            }

            // 特性: 通常特性→夢特性。夢特性が通常特性と同じ場合は1件(隠れ特性ではない)にまとめる
            List<AbilityRef> refs = new ArrayList<>();
            Set<Integer> seenAbilities = new HashSet<>();
            for (String col : abilityCols) {
                Integer ab = optionalInteger(t, r, col, 1, Integer.MAX_VALUE);
                if (!r.get(col).isEmpty() && ab == null) {
                    ok = false;
                } else if (ab != null) {
                    if (!abilities.containsKey(ab)) {
                        problems.error(file, r.line(), col + " " + ab + " は ability.csv にありません");
                        ok = false;
                    } else if (seenAbilities.add(ab)) {
                        refs.add(new AbilityRef(ab, false));
                    } else {
                        problems.warn(file, r.line(), col + " " + ab + " が通常特性の中で重複しています");
                    }
                }
            }
            Integer dream = optionalInteger(t, r, dreamCol, 1, Integer.MAX_VALUE);
            if (!r.get(dreamCol).isEmpty() && dream == null) {
                ok = false;
            } else if (dream != null) {
                if (!abilities.containsKey(dream)) {
                    problems.error(file, r.line(), dreamCol + " " + dream + " は ability.csv にありません");
                    ok = false;
                } else if (seenAbilities.add(dream)) {
                    refs.add(new AbilityRef(dream, true));
                }
            }
            if (refs.isEmpty() && ok) {
                problems.warn(file, r.line(), "特性が1つも設定されていません(id " + id + " " + name + ")");
            }
            if (!ok) {
                continue;
            }
            usedAbilities.addAll(seenAbilities);
            List<Integer> typeIds = type2 == NO_TYPE_ID ? List.of(type1) : List.of(type1, type2);
            result.add(new PokemonForm(id, dexNo, name, region, variant, mega, h, a, b, c, d, s, typeIds, refs));
        }

        resolveMegaSources(file, t, result, transformations);

        for (Map.Entry<Integer, String> e : abilities.entrySet()) {
            if (!usedAbilities.contains(e.getKey())) {
                problems.warn("ability.csv", 0, "どのポケモンも持っていない特性です: id " + e.getKey() + " " + e.getValue());
            }
        }
        return result;
    }

    /** メガ(mega 列が空でない行)の元フォームを、dex_no・region・form が一致するメガ以外の行から探す。 */
    private void resolveMegaSources(String file, CsvTable t, List<PokemonForm> forms,
                                    List<Transformation> transformations) {
        Map<String, List<PokemonForm>> bases = new HashMap<>();
        // forms はエラー行を除いているので、行番号は id から引き直す
        Map<Integer, Integer> lineOf = new HashMap<>();
        for (CsvTable.Row r : t.rows) {
            String idText = r.get("id");
            if (idText.matches("\\d+")) {
                lineOf.put(Integer.parseInt(idText), r.line());
            }
        }
        for (PokemonForm f : forms) {
            if (f.megaKind().isEmpty()) {
                bases.computeIfAbsent(f.dexNo() + "|" + f.region() + "|" + f.variantKey(), k -> new ArrayList<>()).add(f);
            }
        }
        for (PokemonForm f : forms) {
            if (f.megaKind().isEmpty()) {
                continue;
            }
            List<PokemonForm> candidates = bases.getOrDefault(f.dexNo() + "|" + f.region() + "|" + f.variantKey(), List.of());
            int line = lineOf.getOrDefault(f.id(), 0);
            if (candidates.size() == 1) {
                transformations.add(new Transformation(candidates.get(0).id(), f.id(), "MEGA"));
            } else if (candidates.isEmpty()) {
                problems.error(file, line, "メガ形態 id " + f.id() + " " + f.name() + " の元フォームが見つかりません"
                        + "(dex_no=" + f.dexNo() + ", region=\"" + f.region() + "\", form=\"" + f.variantKey()
                        + "\" で mega が空の行がありません)");
            } else {
                problems.error(file, line, "メガ形態 id " + f.id() + " " + f.name() + " の元フォームが複数あります: "
                        + candidates.stream().map(c -> String.valueOf(c.id())).toList());
            }
        }
    }
}
