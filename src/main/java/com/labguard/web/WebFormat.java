package com.labguard.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 页面时间文案统一在这里格式化，避免模板依赖额外方言。
 */
public final class WebFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String EMPTY = "—";

    private WebFormat() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? EMPTY : DATE_TIME.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? EMPTY : DATE.format(value);
    }
}
