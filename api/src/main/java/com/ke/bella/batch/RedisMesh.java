package com.ke.bella.batch;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.params.XTrimParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class RedisMesh {

    public interface MessageListener {
        private void onMessage(Event e) {
            if(StringUtils.hasText(e.context)) {
                BellaContext.replace(e.context);
            }
            try {
                processMessage(e);
            } finally {
                BellaContext.clearAll();
            }
        }

        default void processMessage(Event e) {

        }

        default void onPrivateMessage(Event e) {
            onMessage(e);
        }

        default void onBroadcastMessage(Event e) {
            onMessage(e);
        }
    }

    @FunctionalInterface
    interface EventCallback {
        void onEvent(Event event);
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        String name;
        String from;
        String payload;
        String context;
        long timestamp;
    }

    @Getter
    private final String profile;
    @Getter
    private final String instanceId;
    @Getter
    private final String broadcastTopic;
    @Getter
    private final String privateTopicPrefix;
    @Getter
    private final String instanceStreamKey;
    @Getter
    private final String broadcastStreamKey;
    @Getter
    private final String groupStreamKey;
    private final MessageListener defaultListener;
    private final JedisPool jedisPool;
    private final AtomicBoolean running;
    private final AtomicLong lastRefreshTime = new AtomicLong(0);
    private final long keepFromSecs;
    private final Map<String, MessageListener> listeners;
    private final MeterRegistry meterRegistry;

    private static final String BROADCAST_STREAM_PREFIX = "redis-mesh:broadcast-stream:";
    private static final String PRIVATE_STREAM_PREFIX = "redis-mesh:private-stream:";
    private static final String GROUP_STREAM_PREFIX = "redis-mesh:group-stream:";
    private static final MessageListener DoNothingListener = new MessageListener() {
    };

    public RedisMesh(String profile, String instanceId, String broadcastTopic, JedisPool pool, MeterRegistry meterRegistry) {
        this(profile, instanceId, broadcastTopic, 60L, DoNothingListener, pool, meterRegistry);
    }

    public RedisMesh(String profile, String instanceId, String broadcastTopic, long keepFromSecs, MessageListener listener,
            JedisPool pool, MeterRegistry meterRegistry) {
        this.profile = profile;
        this.instanceId = instanceId;
        this.broadcastTopic = broadcastTopic;
        this.privateTopicPrefix = PRIVATE_STREAM_PREFIX + profile + ":";
        this.instanceStreamKey = String.format("%s%s:%s", PRIVATE_STREAM_PREFIX, profile, instanceId);
        this.broadcastStreamKey = String.format("%s%s:%s", BROADCAST_STREAM_PREFIX, profile, broadcastTopic);
        this.groupStreamKey = String.format("%s%s:%s", GROUP_STREAM_PREFIX, profile, broadcastTopic);
        this.keepFromSecs = keepFromSecs;
        this.defaultListener = listener;
        this.jedisPool = pool;
        this.meterRegistry = meterRegistry;
        this.running = new AtomicBoolean(false);
        this.listeners = new ConcurrentHashMap<>();
    }

    public void start() {
        if(this.running.get()) {
            return;
        }
        this.running.set(true);
        createGroupIfNeeded();
        startMessageConsumers();
    }

    private void createGroupIfNeeded() {
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                jedis.xgroupCreate(groupStreamKey, broadcastTopic, StreamEntryID.LAST_ENTRY, true);
            } catch (Exception e) {
                log.warn("Failed to create group {}: {}", broadcastTopic, e.getMessage());
            }
        }
    }

    private void startMessageConsumers() {
        TaskExecutor.submit(this::consumeBroadcastMessages);
        TaskExecutor.submit(this::consumePrivateMessages);
        TaskExecutor.submit(this::consumeGroupMessages);
    }

    public void registerListener(String name, MessageListener listener) {
        this.listeners.putIfAbsent(name, listener);
    }

    public void sendPrivateMessage(String targetInstanceId, Event event) {
        String streamKey = String.format("%s%s:%s", PRIVATE_STREAM_PREFIX, profile, targetInstanceId);
        sendMessage(streamKey, event);
    }

    public void sendBroadcastMessage(Event event) {
        sendMessage(broadcastStreamKey, event);
    }

    public void sendGroupMessage(Event event) {
        sendMessage(groupStreamKey, event);
    }

    private void sendMessage(String streamKey, Event event) {
        Map<String, String> message = convertEventToMap(event);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.xadd(streamKey, XAddParams.xAddParams().minId(String.valueOf(System.currentTimeMillis() - keepFromSecs * 1000)), message);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to send message to topic: " + streamKey, e);
        }
    }

    public void clear() {
        this.running.set(false);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(instanceStreamKey);
        } catch (Exception e) {
            log.warn("Failed to delete instance stream key on shutdown: {}", instanceStreamKey, e);
        }
    }

    /**
     * 刷新私有 channel 的状态，包含两项维护操作：
     * <ol>
     *   <li>续期 TTL：instanceStreamKey 设有过期时间，正常关闭时由 clear() 主动删除；
     *       若实例异常宕机，依赖 TTL 自动清除，避免 key 永久残留。
     *       每次有消息到达时触发续期，保证活跃实例的 channel 不会意外过期。</li>
     *   <li>清理旧数据：定期用 XTRIM MINID 清理 10 分钟前的数据，防止消息堆积导致内存泄漏。</li>
     * </ol>
     * 通过 lastRefreshTime 限流，2 秒内最多执行一次，避免高频消息时重复操作 Redis。
     */
    public void refreshPrivateChannel() {
        if(!running.get()) {
            return;
        }
        long last = lastRefreshTime.get();
        long now = System.currentTimeMillis();
        if(now - last < 2_000) {
            return;
        }
        if(!lastRefreshTime.compareAndSet(last, now)) {
            return;
        }
        TaskExecutor.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.expire(instanceStreamKey, Configs.PRIVATE_CHANNEL_TTL_SECONDS);
                jedis.xtrim(instanceStreamKey, XTrimParams.xTrimParams()
                        .minId(String.valueOf(now - 60 * 1000 * 10)).approximateTrimming());
            } catch (Exception e) {
                log.warn("Failed to refresh instance stream key: {}", instanceStreamKey, e);
            }
        });
    }

    private MessageListener getListener(Event e) {
        MessageListener listener = this.listeners.get(e.name);
        return listener == null ? defaultListener : listener;
    }

    private void consumePrivateMessages() {
        consumeNormalMessages(instanceStreamKey, e -> getListener(e).onPrivateMessage(e));
    }

    private void consumeBroadcastMessages() {
        consumeNormalMessages(broadcastStreamKey, e -> getListener(e).onBroadcastMessage(e));
    }

    private void consumeGroupMessages() {
        consumeGroupMessages(groupStreamKey, e -> getListener(e).onMessage(e));
    }

    private void consumeNormalMessages(String streamKey, EventCallback callback) {
        String lastId = String.format("%s-0", System.currentTimeMillis());
        Timer forwardLatencyTimer = Timer.builder("redis_mesh.forward.latency")
                .tag("stream", streamKey)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        while (running.get()) {
            try (Jedis jedis = jedisPool.getResource()) {
                List<Map.Entry<String, List<StreamEntry>>> sentries = jedis.xread(
                        XReadParams.xReadParams().count(1000).block(1000),
                        ImmutableMap.of(streamKey, new StreamEntryID(lastId)));

                if(CollectionUtils.isEmpty(sentries)) {
                    continue;
                }

                List<StreamEntry> entries = sentries.get(0).getValue();
                for (StreamEntry entry : entries) {
                    Event event = convertMapToEvent(entry.getFields());
                    lastId = entry.getID().toString();
                    try {
                        callback.onEvent(event);
                        if(event.getTimestamp() > 0) {
                            long latencyMs = System.currentTimeMillis() - event.getTimestamp();
                            forwardLatencyTimer.record(latencyMs, TimeUnit.MILLISECONDS);
                        }
                    } catch (Exception e) {
                        log.error("FAILED_MESSAGE|eventName:{}|payload:{}|error:{}",
                                event.name, event.payload, e.getMessage(), e);
                    }
                }
                jedis.xtrim(streamKey, XTrimParams.xTrimParams().minId(lastId).approximateTrimming());
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void consumeGroupMessages(String streamKey, EventCallback callback) {
        while (running.get()) {
            try (Jedis jedis = jedisPool.getResource()) {
                List<Map.Entry<String, List<StreamEntry>>> sentries = jedis.xreadGroup(
                        broadcastTopic, instanceId,
                        XReadGroupParams.xReadGroupParams().count(1).block(1000),
                        ImmutableMap.of(streamKey, StreamEntryID.UNRECEIVED_ENTRY));

                if(CollectionUtils.isEmpty(sentries)) {
                    continue;
                }

                List<StreamEntry> entries = sentries.get(0).getValue();
                if(!CollectionUtils.isEmpty(entries)) {
                    StreamEntry entry = entries.get(0);
                    Event event = convertMapToEvent(entry.getFields());

                    try {
                        callback.onEvent(event);
                    } catch (Exception e) {
                        log.error("FAILED_MESSAGE|eventName:{}|payload:{}|error:{}",
                                event.name, event.payload, e.getMessage(), e);
                    } finally {
                        jedis.xack(streamKey, broadcastTopic, entry.getID());
                    }
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private Map<String, String> convertEventToMap(Event event) {
        Map<String, String> map = new HashMap<>();
        map.put("name", event.name);
        map.put("from", instanceId);
        map.put("payload", event.payload);
        map.put("context", event.context);
        return map;
    }

    private Event convertMapToEvent(Map<String, String> map) {
        Event event = new Event();
        event.name = map.get("name");
        event.from = map.get("from");
        event.payload = map.get("payload");
        event.context = map.get("context");
        event.timestamp = MapUtils.getLongValue(map, "timestamp", 0);
        return event;
    }
}
