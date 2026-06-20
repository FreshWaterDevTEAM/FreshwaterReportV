package com.freshwater.report.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化工具（本地时区）。
 */
public final class TimeFormat {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeFormat() {
    }

    public static String format(Instant instant) {
        return instant == null ? "-" : FORMATTER.format(instant);
    }
}
