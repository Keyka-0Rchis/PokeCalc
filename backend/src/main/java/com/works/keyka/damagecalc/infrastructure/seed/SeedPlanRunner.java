package com.works.keyka.damagecalc.infrastructure.seed;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/** {@link SqlBatch} の列をJDBCで実行する。トランザクションは呼び出し側(Flyway)が管理する。 */
public final class SeedPlanRunner {

    private SeedPlanRunner() {
    }

    public static void run(Connection connection, List<SqlBatch> plan) throws SQLException {
        for (SqlBatch batch : plan) {
            try {
                if (!batch.parameterized()) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(batch.sql());
                    }
                } else if (!batch.rows().isEmpty()) {
                    try (PreparedStatement ps = connection.prepareStatement(batch.sql())) {
                        for (List<Object> row : batch.rows()) {
                            for (int i = 0; i < row.size(); i++) {
                                bind(ps, i + 1, row.get(i));
                            }
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (SQLException e) {
                throw new SQLException("シード投入に失敗しました: " + batch.description() + " / " + e.getMessage(), e);
            }
        }
    }

    private static void bind(PreparedStatement ps, int index, Object value) throws SQLException {
        switch (value) {
            case null -> ps.setNull(index, Types.NULL);
            case Integer v -> ps.setInt(index, v);
            case Long v -> ps.setLong(index, v);
            case String v -> ps.setString(index, v);
            case BigDecimal v -> ps.setBigDecimal(index, v);
            case Boolean v -> ps.setBoolean(index, v);
            default -> throw new IllegalArgumentException("未対応の引数型です: " + value.getClass());
        }
    }
}
