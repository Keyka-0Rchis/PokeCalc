package com.works.keyka.damagecalc.infrastructure.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link SqlBatch} をSQLの文字列にする(引数をリテラルに展開)。
 * 実行用ではなく、生成されるSQLの確認・テスト用(psql に流して検証できる)。
 */
public final class SeedSqlText {

    private SeedSqlText() {
    }

    public static String render(List<SqlBatch> plan) {
        StringBuilder sb = new StringBuilder("begin;\n");
        for (SqlBatch batch : plan) {
            sb.append("-- ").append(batch.description()).append('\n');
            if (!batch.parameterized()) {
                sb.append(batch.sql()).append(";\n");
                continue;
            }
            for (List<Object> row : batch.rows()) {
                sb.append(substitute(batch.sql(), row)).append(";\n");
            }
        }
        return sb.append("commit;\n").toString();
    }

    private static String substitute(String sql, List<Object> row) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?') {
                out.append(literal(row.get(next++)));
            } else {
                out.append(c);
            }
        }
        if (next != row.size()) {
            throw new IllegalStateException("プレースホルダ数と引数数が合いません: " + sql);
        }
        return out.toString();
    }

    private static String literal(Object v) {
        return switch (v) {
            case null -> "null";
            case Integer n -> n.toString();
            case Long n -> n.toString();
            case BigDecimal n -> n.toPlainString();
            case Boolean b -> b ? "true" : "false";
            case String s -> "'" + s.replace("'", "''") + "'";
            default -> throw new IllegalArgumentException("未対応の引数型です: " + v.getClass());
        };
    }
}
