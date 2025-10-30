package com.ke.bella.batch.utils;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

public class TimeUtilsTest {

    @Test
    public void testToEpochMilli_WithValidDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
        Long result = TimeUtils.toEpochMilli(dateTime);

        assertNotNull(result);
        // 2025-01-01 12:00:00 UTC+8 = 2025-01-01 04:00:00 UTC = 1735704000000L
        assertEquals(Long.valueOf(1735704000000L), result);
    }

    @Test
    public void testToEpochMilli_WithNullDateTime() {
        Long result = TimeUtils.toEpochMilli(null);
        assertNull(result);
    }

    @Test
    public void testFromEpochMilli_WithValidTimestamp() {
        long epochMilli = 1735704000000L; // 2025-01-01 04:00:00 UTC = 2025-01-01 12:00:00 UTC+8
        LocalDateTime result = TimeUtils.fromEpochMilli(epochMilli);

        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0, 0), result);
    }

    @Test
    public void testRoundTripConversion() {
        LocalDateTime original = LocalDateTime.of(2025, 6, 15, 14, 30, 45);

        Long epochMilli = TimeUtils.toEpochMilli(original);
        LocalDateTime converted = TimeUtils.fromEpochMilli(epochMilli);

        assertEquals(original, converted);
    }

    @Test
    public void testTimeZoneConsistency() {
        // Test that the time zone offset is consistently applied (UTC+8)
        LocalDateTime dateTime = LocalDateTime.of(2025, 7, 1, 0, 0, 0);
        Long epochMilli = TimeUtils.toEpochMilli(dateTime);

        // Convert back and verify
        LocalDateTime converted = TimeUtils.fromEpochMilli(epochMilli);
        assertEquals(dateTime, converted);

        // Verify that the conversion accounts for UTC+8 offset
        long expectedEpochMilli = dateTime.atZone(ZoneOffset.ofHours(8)).toInstant().toEpochMilli();
        assertEquals(expectedEpochMilli, epochMilli.longValue());
    }

    @Test
    public void testEdgeCases() {
        // Test epoch time (1970-01-01 00:00:00 UTC = 1970-01-01 08:00:00 UTC+8)
        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 8, 0, 0);
        Long epochResult = TimeUtils.toEpochMilli(epoch);
        assertEquals(Long.valueOf(0L), epochResult);

        // Test year 2000 (commonly used as default in database)
        LocalDateTime year2000 = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        Long year2000Result = TimeUtils.toEpochMilli(year2000);
        LocalDateTime year2000Converted = TimeUtils.fromEpochMilli(year2000Result);
        assertEquals(year2000, year2000Converted);
    }
}
