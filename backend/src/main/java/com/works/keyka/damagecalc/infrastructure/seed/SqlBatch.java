package com.works.keyka.damagecalc.infrastructure.seed;

import java.util.Arrays;
import java.util.List;

/**
 * 実行するSQL1本。rows が null なら引数なしで1回実行、それ以外は行ごとに引数を束ねてバッチ実行する。
 * 引数の型は Integer / Long / String / BigDecimal / Boolean / null のみ。
 */
public record SqlBatch(String description, String sql, List<List<Object>> rows) {

    public boolean parameterized() {
        return rows != null;
    }

    public static SqlBatch plain(String description, String sql) {
        return new SqlBatch(description, sql, null);
    }

    public static SqlBatch withRows(String description, String sql, List<List<Object>> rows) {
        return new SqlBatch(description, sql, rows);
    }

    /** null を含められる行(List.of は null を許さないため)。 */
    public static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
