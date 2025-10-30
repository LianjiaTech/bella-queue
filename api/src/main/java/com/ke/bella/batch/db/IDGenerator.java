package com.ke.bella.batch.db;

import com.ke.bella.batch.enums.ResponseMode;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class IDGenerator {
    private static final String yyMMddHHmmss = "yyMMddHHmmss";
    private static final int MAX_COUNT = 10000000;
    private static String instanceId;

    public static final String TASK_PREFIX = "TASK";
    public static final String BATCH_PREFIX = "BATCH";

    public static final IDGenerator QUEUE_TASK_GEN = new IDGenerator(TASK_PREFIX);
    public static final IDGenerator QUEUE_BATCH_GEN = new IDGenerator(BATCH_PREFIX);

    private final int serialLength;
    private final int serialMask;
    private final String prefix;
    private final String serialFormat;
    private final AtomicInteger serialCounter = new AtomicInteger(0);

    public IDGenerator(String prefix) {
        this(prefix, 6);
    }

    public IDGenerator(String prefix, int serialLength) {
        this.prefix = prefix;
        this.serialLength = serialLength;
        this.serialFormat = "%0" + this.serialLength + "d";
        this.serialMask = Integer.parseInt("1" + String.format(serialFormat, 0));
    }

    private String nextTick() {
        int val = serialCounter.incrementAndGet();
        if(val >= MAX_COUNT) {
            synchronized(serialCounter) {
                val = serialCounter.get();
                if(val >= MAX_COUNT) {
                    serialCounter.set(0);
                }
            }
            val = serialCounter.incrementAndGet();

        }
        return String.format(this.serialFormat, val % this.serialMask);
    }

    public static void setInstanceId(Long id) {
        int idx = id.intValue();
        if(idx > 9999) {
            throw new IllegalStateException("超出当前所能够支持的最大实例数");
        }
        instanceId = String.format("%04d", idx);
    }

    public static String newQueueBatchId(Long queueMetaId, Integer level) {
        return QUEUE_BATCH_GEN.generate(queueMetaId, level);
    }

    public String generate(Long queueMetaId, Integer level) {
        String now = new SimpleDateFormat(yyMMddHHmmss).format(new Date());
        return String.format("%s-%s-%s-%s-%s-%s", this.prefix, queueMetaId, level, now, instanceId, nextTick());
    }

    public static String newQueueTaskId(Long queueMetaId, Integer level, String responseMode) {
        return QUEUE_TASK_GEN.generateTaskId(queueMetaId, level, responseMode);
    }

    public String generateTaskId(Long queueMetaId, Integer level, String responseMode) {
        String now = new SimpleDateFormat(yyMMddHHmmss).format(new Date());
        String simpleResponseMode;
        if(ResponseMode.batch.name().equals(responseMode)) {
            simpleResponseMode = "P";
        } else {
            simpleResponseMode = responseMode.substring(0, 1).toUpperCase();
        }
        return String.format("%s-%s-%s-%s-%s-%s-%s", this.prefix, queueMetaId, level, simpleResponseMode, now, instanceId, nextTick());
    }

    public static ResponseMode parseResponseMode(String taskId) {
        String[] parts = taskId.split("-");
        String responseModeStr = parts[3];
        switch (responseModeStr) {
        case "B":
            return ResponseMode.blocking;
        case "S":
            return ResponseMode.streaming;
        case "C":
            return ResponseMode.callback;
        case "P":
            return ResponseMode.batch;
        default:
            throw new IllegalArgumentException("Unknown response mode in taskId: " + responseModeStr);
        }
    }

    public static Long parseQueueId(String taskId) {
        String[] parts = taskId.split("-");
        return Long.parseLong(parts[1]);
    }

    public static Integer parseLevel(String taskId) {
        String[] parts = taskId.split("-");
        return Integer.parseInt(parts[2]);
    }

    public static LocalDateTime parseTimestamp(String taskId) {
        String[] parts = taskId.split("-");
        String timestampStr = parts[4];
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yyMMddHHmmss);
        return LocalDateTime.parse(timestampStr, formatter);
    }

    public static Long parseQueueIdFromBatchId(String batchId) {
        return Long.parseLong(batchId.split("-")[1]);
    }

    public static Integer parseLevelFromBatchId(String batchId) {
        return Integer.parseInt(batchId.split("-")[2]);
    }

    public static LocalDateTime parseTimestampFromBatchId(String batchId) {
        String timestampStr = batchId.split("-")[3];
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yyMMddHHmmss);
        return LocalDateTime.parse(timestampStr, formatter);
    }
}
