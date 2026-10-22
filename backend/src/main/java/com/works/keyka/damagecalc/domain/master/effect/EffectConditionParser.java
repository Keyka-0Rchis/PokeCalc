package com.works.keyka.damagecalc.domain.master.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 効果の condition 列(例: {@code type_id:16;opponent_ability_id!=188})を解析・検証する。
 * 仕様: docs/master-data-spec.md §5, §6
 *
 * <p>';' 区切りの各項目は、次のいずれか。
 * <ul>
 *   <li>条件: {@code key:value} / {@code key<=value} など(AND結合)</li>
 *   <li>単独の条件語: {@code critical}</li>
 *   <li>パラメータ: {@code from:} {@code to:} {@code rule:}(条件ではなく {@link Result#params()} に分ける)</li>
 * </ul>
 */
public final class EffectConditionParser {

    /** マスタ参照の存在確認。 */
    public interface Lookup {
        boolean typeExists(int id);

        boolean abilityExists(int id);

        boolean moveFlagExists(int id);
    }

    /** 解析した条件1件。op は ":" "=" "!=" "<" "<=" ">" ">=" のいずれか。 */
    public record Atom(String key, String op, String value) {
        @Override
        public String toString() {
            return key + op + value;
        }
    }

    /** 解析結果。errors が空でなければ、他の値は信用しない。 */
    public record Result(List<Atom> atoms, Map<String, String> params, List<String> errors) {
        /** 条件を元の形式に戻した文字列。条件がなければ null。 */
        public String conditionsText() {
            if (atoms.isEmpty()) {
                return null;
            }
            List<String> parts = new ArrayList<>();
            for (Atom a : atoms) {
                parts.add(a.key().equals("critical") && a.value().isEmpty() ? "critical" : a.toString());
            }
            return String.join(";", parts);
        }
    }

    private static final Pattern TOKEN = Pattern.compile("^([a-z_]+)(>=|<=|!=|<|>|=|:)(.+)$");
    private static final Pattern BARE = Pattern.compile("^[a-z_]+$");
    private static final Pattern NUMBER = Pattern.compile("^(\\d+/\\d+|\\d+(\\.\\d+)?|\\.\\d+)$");
    private static final Pattern INTEGER = Pattern.compile("^\\d+$");
    private static final Pattern RULE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private static final Set<String> PARAM_KEYS = Set.of("from", "to", "rule");
    private static final Set<String> WEATHERS = Set.of("sunny", "rain", "sand_storm", "snow");
    private static final Set<String> FIELDS = Set.of("grass", "electric", "psychic", "misty");
    private static final Set<String> BAD_CONDITIONS =
            Set.of("any", "poisoned", "burned", "paralyzed", "asleep", "frozen");
    private static final Set<String> CATEGORIES = Set.of("physical", "special", "status");
    private static final Set<String> COMPARISON_OPS = Set.of(":", "=", "!=", "<", "<=", ">", ">=");

    private EffectConditionParser() {
    }

    /**
     * @param raw    condition 列の文字列(空・null可)
     * @param target この行の effect_target(null可。to: / from: の意味の検証に使う)
     */
    public static Result parse(String raw, EffectTarget target, Lookup lookup) {
        List<Atom> atoms = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new Result(atoms, params, errors);
        }
        for (String rawToken : raw.split(";", -1)) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                errors.add("condition に空の項目があります(';' が連続している、または末尾にあります): \"" + raw + "\"");
                continue;
            }
            Matcher m = TOKEN.matcher(token);
            if (!m.matches()) {
                if (BARE.matcher(token).matches()) {
                    if (token.equals("critical")) {
                        atoms.add(new Atom("critical", "", ""));
                    } else {
                        errors.add("条件 \"" + token + "\" は値のない形では使えません(未知の条件語、または ':' の書き忘れ)");
                    }
                } else {
                    errors.add("条件 \"" + token + "\" を解釈できません(key:value や key<=value の形で書いてください)");
                }
                continue;
            }
            String key = m.group(1);
            String op = m.group(2);
            String value = m.group(3).trim();
            if (PARAM_KEYS.contains(key)) {
                parseParam(key, op, value, target, lookup, params, errors);
            } else {
                parseCondition(key, op, value, lookup, atoms, errors);
            }
        }
        return new Result(atoms, params, errors);
    }

    private static void parseParam(String key, String op, String value, EffectTarget target, Lookup lookup,
                                   Map<String, String> params, List<String> errors) {
        if (!op.equals(":")) {
            errors.add("\"" + key + "\" は ':' で指定してください: " + key + op + value);
            return;
        }
        if (params.containsKey(key)) {
            errors.add("\"" + key + "\" が複数指定されています");
            return;
        }
        switch (key) {
            case "rule" -> {
                if (!RULE_KEY.matcher(value).matches()) {
                    errors.add("rule は大文字英字とアンダースコアで指定してください: rule:" + value);
                    return;
                }
            }
            case "from" -> {
                if (target != EffectTarget.OWN_MOVE_TYPE) {
                    errors.add("from: は effect_target が own_move_type の行でのみ使えます");
                    return;
                }
                if (!value.equals("any") && !isKnownType(value, lookup)) {
                    errors.add("from: に未知のタイプidがあります: from:" + value);
                    return;
                }
            }
            case "to" -> {
                if (target == EffectTarget.OWN_MOVE_TYPE) {
                    if (!isKnownType(value, lookup)) {
                        errors.add("to: に未知のタイプidがあります: to:" + value);
                        return;
                    }
                } else if (target == EffectTarget.WEATHER || target == EffectTarget.OWN_WEATHER) {
                    if (!value.equals("none") && !WEATHERS.contains(value)) {
                        errors.add("to: に未知の天候があります(none / sunny / rain / sand_storm / snow): to:" + value);
                        return;
                    }
                } else {
                    errors.add("to: は effect_target が own_move_type / weather / own_weather の行でのみ使えます");
                    return;
                }
            }
            default -> throw new IllegalStateException(key);
        }
        params.put(key, value);
    }

    private static void parseCondition(String key, String op, String value, Lookup lookup,
                                       List<Atom> atoms, List<String> errors) {
        String where = "条件 \"" + key + op + value + "\": ";
        switch (key) {
            case "type_id", "original_type_id" -> {
                if (!op.equals(":")) {
                    errors.add(where + "':' で指定してください");
                    return;
                }
                if (!isKnownType(value, lookup)) {
                    errors.add(where + "未知のタイプidです");
                    return;
                }
            }
            case "flag_id" -> {
                if (!op.equals(":")) {
                    errors.add(where + "':' で指定してください");
                    return;
                }
                if (!INTEGER.matcher(value).matches() || !lookup.moveFlagExists(Integer.parseInt(value))) {
                    errors.add(where + "未知の技フラグidです");
                    return;
                }
            }
            case "opponent_ability_id" -> {
                if (!op.equals(":") && !op.equals("!=")) {
                    errors.add(where + "':' または '!=' で指定してください");
                    return;
                }
                if (!INTEGER.matcher(value).matches() || !lookup.abilityExists(Integer.parseInt(value))) {
                    errors.add(where + "未知の特性idです");
                    return;
                }
            }
            case "category" -> {
                if (!op.equals(":") || !CATEGORIES.contains(value)) {
                    errors.add(where + "category:physical / special / status のいずれかを指定してください");
                    return;
                }
            }
            case "weather" -> {
                if (!op.equals(":") || !WEATHERS.contains(value)) {
                    errors.add(where + "未知の天候です(sunny / rain / sand_storm / snow)");
                    return;
                }
            }
            case "field" -> {
                if (!op.equals(":") || !FIELDS.contains(value)) {
                    errors.add(where + "未知のフィールドです(grass / electric / psychic / misty)");
                    return;
                }
            }
            case "own_bad_condition" -> {
                if (!op.equals(":") || !BAD_CONDITIONS.contains(value)) {
                    errors.add(where + "未知の状態異常です(any / poisoned / burned / paralyzed / asleep / frozen)");
                    return;
                }
            }
            case "dex_no" -> {
                if (!op.equals(":") || !INTEGER.matcher(value).matches()) {
                    errors.add(where + "dex_no:整数 の形で指定してください");
                    return;
                }
            }
            case "power" -> {
                if (!COMPARISON_OPS.contains(op) || !INTEGER.matcher(value).matches()) {
                    errors.add(where + "power は整数との比較で指定してください");
                    return;
                }
            }
            case "type_effectiveness", "own_hp_ratio" -> {
                if (!COMPARISON_OPS.contains(op) || !NUMBER.matcher(value).matches()) {
                    errors.add(where + "数値または分数(1/3 など)との比較で指定してください");
                    return;
                }
                if (value.contains("/") && value.endsWith("/0")) {
                    errors.add(where + "0で割る分数です");
                    return;
                }
            }
            default -> {
                errors.add(where + "未知の条件キー \"" + key + "\" です(誤字、または文法に未登録のキー)");
                return;
            }
        }
        atoms.add(new Atom(key, op, value));
    }

    private static boolean isKnownType(String value, Lookup lookup) {
        return INTEGER.matcher(value).matches() && lookup.typeExists(Integer.parseInt(value));
    }
}
