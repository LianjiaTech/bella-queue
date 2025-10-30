package com.ke.bella.batch.enums;

public enum ResponseMode {
    blocking, streaming, callback, batch;

    public static boolean isOnlineMode(String mode) {
        return "blocking".equals(mode) || "streaming".equals(mode);
    }

    public static boolean isOfflineMode(String mode) {
        return "callback".equals(mode) || "batch".equals(mode);
    }
}
