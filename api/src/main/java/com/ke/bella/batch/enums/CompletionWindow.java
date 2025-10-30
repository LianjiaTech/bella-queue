package com.ke.bella.batch.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum CompletionWindow {
    MINUTE("m", "分钟"),
    HOUR("h", "小时"),
    DAY("d", "天");

    private final String code;
    private final String description;

    public static final String DEFAULT = "24h";

    private static final String PATTERN = "(\\d+)([a-zA-Z]+)";

    public static boolean isNotValid(String timeWindow) {
        return StringUtils.isBlank(timeWindow)
                || !timeWindow.matches(PATTERN)
                || Arrays.stream(values()).noneMatch(window -> timeWindow.endsWith(window.code));
    }

    public static LocalDateTime calculateExpirationTime(LocalDateTime createTime, String completionWindow) {
        String numberPart = completionWindow.replaceAll("[^0-9]", "");
        String unitPart = completionWindow.replaceAll("[0-9]", "");

        int amount = Integer.parseInt(numberPart);

        switch (unitPart.toLowerCase()) {
        case "m":
            return createTime.plusMinutes(amount);
        case "h":
            return createTime.plusHours(amount);
        case "d":
            return createTime.plusDays(amount);
        default:
            return createTime.plusHours(24);
        }
    }
}
