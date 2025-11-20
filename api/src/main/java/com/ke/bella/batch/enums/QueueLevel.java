package com.ke.bella.batch.enums;

import com.ke.bella.batch.service.Configs;
import com.ke.bella.batch.service.FullQueueName;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum QueueLevel {

    L0(0), L1(1);

    private final int level;

    QueueLevel(int level) {
        this.level = level;
    }

    public static boolean isOnline(int level) {
        return L0.getLevel() == level;
    }

    public boolean isOnline() {
        return this == L0;
    }

    public boolean isOffline() {
        return this != L0;
    }

    public static int getCapacity(int level) {
        return level == 0 ? Configs.ONLINE_QUEUE_CAPACITY : Configs.OFFLINE_QUEUE_CAPACITY;
    }

    public static QueueLevel fromLevel(int level) {
        return Arrays.stream(values())
                .filter(queueLevel -> queueLevel.getLevel() == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown queue level: " + level));
    }

    public static QueueLevel fromQueueName(String queueName) {
        FullQueueName fullQueueName = FullQueueName.valueOf(queueName);
        int level = fullQueueName.getLevel();
        return fromLevel(level);
    }

    public static boolean isOnlineQueue(String queueName) {
        return fromQueueName(queueName) == L0;
    }

    public static boolean isOfflineQueue(String queueName) {
        return fromQueueName(queueName) != L0;
    }

}
