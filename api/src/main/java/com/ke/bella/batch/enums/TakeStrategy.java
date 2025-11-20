package com.ke.bella.batch.enums;

import com.ke.bella.batch.service.LuaManager;
import com.ke.bella.batch.service.RedisBlockingQueue;
import com.theokanning.openai.queue.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("all")
public enum TakeStrategy {
    //todo:: 优化实现
    fifo {
        @Override
        @SneakyThrows
        public List<Task> take(List<String> queueNames, int size, QueueProvider queueProvider) {
            if(size > 10) {
                size = 10;
            }

            RedisBlockingQueue queue = (RedisBlockingQueue) queueProvider.getQueue(queueNames.get(0));
            JedisPool jedisPool = queue.getJedisPool();

            List<String> args = new ArrayList<>();
            args.add(String.valueOf(size));
            args.add(String.valueOf(queueNames.size()));
            args.addAll(queueNames);

            Object result = LuaManager.execute(jedisPool, "fifo", args);

            return Optional.ofNullable((List<List<String>>) result)
                    .map(redisResultList -> redisResultList.stream()
                            .filter(taskData -> taskData.size() >= 2 && taskData.get(1) != null)
                            // 解析任务数据：taskDataArray.get(1)包含序列化的任务信息
                            .map(taskData -> queue.parseTask(taskData.get(1)))
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());

        }
    },

    active_passive {
        @Override
        public List<Task> take(List<String> queueNames, int size, QueueProvider queueProvider) {
            List<Task> selectedTasks = new ArrayList<>(size);

            for (String queueName : queueNames) {
                if(selectedTasks.size() >= size) {
                    break;
                }
                BlockingQueue<Task> queue = queueProvider.getQueue(queueName);
                Task task;
                while (selectedTasks.size() < size && (task = queue.poll()) != null) {
                    selectedTasks.add(task);
                }
            }

            return selectedTasks;
        }
    },

    round_robin {
        @Override
        public List<Task> take(List<String> queueNames, int size, QueueProvider queueProvider) {
            List<Task> selectedTasks = new ArrayList<>(size);

            int emptyRounds = 0;
            for (int queueIndex = 0; selectedTasks.size() < size && emptyRounds < queueNames.size()
                    ; queueIndex = (queueIndex + 1) % queueNames.size()) {
                Task task = queueProvider.getQueue(queueNames.get(queueIndex)).poll();
                if(task != null) {
                    selectedTasks.add(task);
                    emptyRounds = 0;
                } else {
                    emptyRounds++;
                }
            }

            return selectedTasks;
        }
    },

    sequential {
        @Override
        @SneakyThrows
        public List<Task> take(List<String> queueNames, int size, QueueProvider queueProvider) {
            if(queueNames.isEmpty()) {
                return Collections.emptyList();
            }

            RedisBlockingQueue queue = (RedisBlockingQueue) queueProvider.getQueue(queueNames.get(0));
            Object result = LuaManager.execute(queue.getJedisPool(), "sequential", queueNames);

            return Optional.ofNullable((List<String>) result)
                    .map(list -> list.stream()
                            .map(queue::parseTask)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());
        }
    };

    public abstract List<Task> take(List<String> queueNames, int size, QueueProvider queueProvider);

    @FunctionalInterface
    public interface QueueProvider {
        BlockingQueue<Task> getQueue(String queueName);
    }

}
