package com.labguard.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SQLite 文本列与 Java 类型之间的转换约定。
 * 时间统一按 ISO-8601 存取，布尔统一按 0/1 存取。
 */
final class RowValues {

    private RowValues() {
    }

    static LocalDateTime dateTime(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }

    static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    /** SQLite 的 REAL 列可空，getDouble 返回 0 时需要用 wasNull 区分"没填"和"填了 0"。 */
    static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    static boolean bool(int value) {
        return value != 0;
    }
}
