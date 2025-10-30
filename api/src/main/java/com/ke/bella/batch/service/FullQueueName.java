package com.ke.bella.batch.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FullQueueName {

    private String queueName;
    private int level;

    public static FullQueueName valueOf(String fullQueueName) {
        String[] parts = fullQueueName.split(":");
        if(parts.length != 2) {
            throw new IllegalArgumentException("Invalid queue name format: " + fullQueueName);
        }

        String queueName = parts[0];
        int level = Integer.parseInt(parts[1]);
        return new FullQueueName(queueName, level);
    }

    public String toString() {
        return queueName + ":" + level;
    }

}
