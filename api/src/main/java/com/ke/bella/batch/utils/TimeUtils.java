package com.ke.bella.batch.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class TimeUtils {

    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static Long toEpochMilli(LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime)
                .map(dt -> dt.atZone(ZONE_OFFSET).toInstant().toEpochMilli())
                .orElse(null);
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli)
                .atZone(ZONE_OFFSET)
                .toLocalDateTime();
    }

    public static LocalDateTime parseTimestamp(String timestampStr) {
        return LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
    }

    public static String formatTimestamp(LocalDateTime dateTime) {
        return dateTime.format(TIMESTAMP_FORMATTER);
    }

    public static long toSeconds(LocalDateTime dateTime) {
        if(dateTime == null) {
            return 0;
        }
        long targetEpochMilli = toEpochMilli(dateTime);
        long currentEpochMilli = System.currentTimeMillis();
        return (targetEpochMilli - currentEpochMilli) / 1000;
    }
}
