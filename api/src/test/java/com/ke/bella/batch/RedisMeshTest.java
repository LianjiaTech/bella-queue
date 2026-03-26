package com.ke.bella.batch;

import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.XAddParams;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(MockitoJUnitRunner.class)
public class RedisMeshTest {

    @Mock
    private JedisPool mockJedisPool;

    @Mock
    private Jedis mockJedis;

    @Mock
    private RedisMesh.MessageListener mockListener;

    // 使用 SimpleMeterRegistry 避免 mock Timer 的复杂性
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RedisMesh redisMesh;

    private static final String TEST_PROFILE = "test-profile";
    private static final String TEST_INSTANCE_ID = "instance-1";
    private static final String TEST_BROADCAST_TOPIC = "broadcast";
    // start() 提交的消费线程数：1(private) + 1(broadcast) + 1(group)
    private static final int CONSUMER_THREAD_COUNT = 3;

    @Before
    public void setUp() {
        when(mockJedisPool.getResource()).thenReturn(mockJedis);
        redisMesh = new RedisMesh(TEST_PROFILE, TEST_INSTANCE_ID, TEST_BROADCAST_TOPIC, mockJedisPool, meterRegistry);
    }

    private void setRunning(boolean value) throws Exception {
        java.lang.reflect.Field runningField = RedisMesh.class.getDeclaredField("running");
        runningField.setAccessible(true);
        ((AtomicBoolean) runningField.get(redisMesh)).set(value);
    }

    @Test
    public void testConstructor_BasicConstructor() {
        RedisMesh mesh = new RedisMesh(TEST_PROFILE, TEST_INSTANCE_ID, TEST_BROADCAST_TOPIC, mockJedisPool, meterRegistry);

        assertEquals(TEST_PROFILE, mesh.getProfile());
        assertEquals(TEST_INSTANCE_ID, mesh.getInstanceId());
        assertEquals(TEST_BROADCAST_TOPIC, mesh.getBroadcastTopic());
        assertEquals("redis-mesh:private-stream:test-profile:", mesh.getPrivateTopicPrefix());
        assertEquals("redis-mesh:private-stream:test-profile:instance-1", mesh.getInstanceStreamKey());
        assertEquals("redis-mesh:broadcast-stream:test-profile:broadcast", mesh.getBroadcastStreamKey());
        assertEquals("redis-mesh:group-stream:test-profile:broadcast", mesh.getGroupStreamKey());
    }

    @Test
    public void testConstructor_FullConstructor() {
        long keepFromSecs = 120L;

        RedisMesh mesh = new RedisMesh(TEST_PROFILE, TEST_INSTANCE_ID, TEST_BROADCAST_TOPIC,
                keepFromSecs, mockListener, mockJedisPool, meterRegistry);

        assertEquals(TEST_PROFILE, mesh.getProfile());
        assertEquals(TEST_INSTANCE_ID, mesh.getInstanceId());
        assertEquals(TEST_BROADCAST_TOPIC, mesh.getBroadcastTopic());
    }

    @Test
    public void testStart_CreatesGroupAndStartsConsumers() {
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            redisMesh.start();

            verify(mockJedis).xgroupCreate(anyString(), eq(TEST_BROADCAST_TOPIC),
                    eq(StreamEntryID.LAST_ENTRY), eq(true));

            mockedTaskExecutor.verify(() -> TaskExecutor.submit(any(Runnable.class)), times(CONSUMER_THREAD_COUNT));
        }
    }

    @Test
    public void testStart_AlreadyRunning() {
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            redisMesh.start();
            redisMesh.start(); // Second call should do nothing

            // Should only create group once
            verify(mockJedis, times(1)).xgroupCreate(anyString(), eq(TEST_BROADCAST_TOPIC),
                    eq(StreamEntryID.LAST_ENTRY), eq(true));

            mockedTaskExecutor.verify(() -> TaskExecutor.submit(any(Runnable.class)), times(CONSUMER_THREAD_COUNT));
        }
    }

    @Test
    public void testStart_GroupCreationFails() {
        doThrow(new JedisException("Group already exists")).when(mockJedis)
                .xgroupCreate(anyString(), anyString(), any(StreamEntryID.class), anyBoolean());

        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            redisMesh.start(); // Should not throw exception

            mockedTaskExecutor.verify(() -> TaskExecutor.submit(any(Runnable.class)), times(CONSUMER_THREAD_COUNT));
        }
    }

    @Test
    public void testRegisterListener() {
        String listenerName = "test-listener";

        redisMesh.registerListener(listenerName, mockListener);

        // Register same name again, should not replace
        RedisMesh.MessageListener anotherListener = mock(RedisMesh.MessageListener.class);
        redisMesh.registerListener(listenerName, anotherListener);
    }

    @Test
    public void testSendPrivateMessage() {
        String targetInstanceId = "target-instance";
        // payload 非 JSON，parseTaskIdFromPayload 返回 null，shard=0，使用原始 key（无后缀）
        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("test-event")
                .payload("test-payload")
                .context("test-context")
                .build();

        redisMesh.sendPrivateMessage(targetInstanceId, event);

        String expectedStreamKey = "redis-mesh:private-stream:test-profile:target-instance";
        verify(mockJedis).xadd(eq(expectedStreamKey), any(XAddParams.class), any(Map.class));
    }

    @Test
    public void testSendBroadcastMessage() {
        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("broadcast-event")
                .payload("broadcast-payload")
                .build();

        redisMesh.sendBroadcastMessage(event);

        verify(mockJedis).xadd(eq(redisMesh.getBroadcastStreamKey()), any(XAddParams.class), any(Map.class));
    }

    @Test
    public void testSendGroupMessage() {
        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("group-event")
                .payload("group-payload")
                .build();

        redisMesh.sendGroupMessage(event);

        verify(mockJedis).xadd(eq(redisMesh.getGroupStreamKey()), any(XAddParams.class), any(Map.class));
    }

    @Test(expected = RuntimeException.class)
    public void testSendMessage_JedisException() {
        when(mockJedis.xadd(anyString(), any(XAddParams.class), any(Map.class)))
                .thenThrow(new JedisException("Redis error"));

        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("test-event")
                .payload("test-payload")
                .build();

        redisMesh.sendBroadcastMessage(event);
    }

    @Test
    public void testShutdown() {
        redisMesh.clear();
        // clear() 删除所有分片 key，不应抛出异常
    }

    @Test
    public void testEventBuilder() {
        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("test-name")
                .from("test-from")
                .payload("test-payload")
                .context("test-context")
                .build();

        assertEquals("test-name", event.getName());
        assertEquals("test-from", event.getFrom());
        assertEquals("test-payload", event.getPayload());
        assertEquals("test-context", event.getContext());
    }

    @Test
    public void testEventSettersAndGetters() {
        RedisMesh.Event event = new RedisMesh.Event();

        event.setName("test-name");
        event.setFrom("test-from");
        event.setPayload("test-payload");
        event.setContext("test-context");

        assertEquals("test-name", event.getName());
        assertEquals("test-from", event.getFrom());
        assertEquals("test-payload", event.getPayload());
        assertEquals("test-context", event.getContext());
    }

    @Test
    public void testEventAllArgsConstructor() {
        RedisMesh.Event event = new RedisMesh.Event("test-name", "test-from", "test-payload", "test-context", 0L);

        assertEquals("test-name", event.getName());
        assertEquals("test-from", event.getFrom());
        assertEquals("test-payload", event.getPayload());
        assertEquals("test-context", event.getContext());
    }

    @Test
    public void testMessageListener_OnMessage_WithContext() {
        RedisMesh.MessageListener listener = new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event e) {
                assertNotNull(BellaContext.getOperator());
            }
        };

        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("test")
                .context("{\"operator\":{\"userId\":123,\"userName\":\"test\"}}")
                .build();

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {
            mockedBellaContext.when(() -> BellaContext.getOperator()).thenReturn(
                    Operator.builder().userId(123L).userName("test").build());

            try {
                java.lang.reflect.Method method = RedisMesh.MessageListener.class.getDeclaredMethod("onMessage", RedisMesh.Event.class);
                method.setAccessible(true);
                method.invoke(listener, event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            mockedBellaContext.verify(() -> BellaContext.replace(event.context));
            mockedBellaContext.verify(BellaContext::clearAll);
        }
    }

    @Test
    public void testMessageListener_OnMessage_NoContext() {
        AtomicBoolean processMessageCalled = new AtomicBoolean(false);

        RedisMesh.MessageListener listener = new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event e) {
                processMessageCalled.set(true);
            }
        };

        RedisMesh.Event event = RedisMesh.Event.builder()
                .name("test")
                .build();

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {
            try {
                java.lang.reflect.Method method = RedisMesh.MessageListener.class.getDeclaredMethod("onMessage", RedisMesh.Event.class);
                method.setAccessible(true);
                method.invoke(listener, event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertTrue(processMessageCalled.get());
            mockedBellaContext.verify(() -> BellaContext.replace(anyString()), never());
            mockedBellaContext.verify(BellaContext::clearAll);
        }
    }

    @Test
    public void testMessageListener_OnPrivateMessage() {
        AtomicBoolean onMessageCalled = new AtomicBoolean(false);

        RedisMesh.MessageListener listener = new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event e) {
                onMessageCalled.set(true);
            }
        };

        RedisMesh.Event event = RedisMesh.Event.builder().name("test").build();

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {
            listener.onPrivateMessage(event);
            assertTrue(onMessageCalled.get());
        }
    }

    @Test
    public void testMessageListener_OnBroadcastMessage() {
        AtomicBoolean onMessageCalled = new AtomicBoolean(false);

        RedisMesh.MessageListener listener = new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event e) {
                onMessageCalled.set(true);
            }
        };

        RedisMesh.Event event = RedisMesh.Event.builder().name("test").build();

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {
            listener.onBroadcastMessage(event);
            assertTrue(onMessageCalled.get());
        }
    }

    @Test
    public void testRefreshPrivateChannel_CallsExpire() throws Exception {
        setRunning(true);
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            mockedTaskExecutor.when(() -> TaskExecutor.submit(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    });

            redisMesh.refreshPrivateChannel();

            verify(mockJedis, times(1)).expire(anyString(), eq((long) Configs.PRIVATE_CHANNEL_TTL_SECONDS));
        }
    }

    @Test
    public void testRefreshPrivateChannel_ThrottledWithin10Seconds() throws Exception {
        setRunning(true);
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            mockedTaskExecutor.when(() -> TaskExecutor.submit(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    });

            redisMesh.refreshPrivateChannel();
            redisMesh.refreshPrivateChannel(); // 2秒内第二次调用应被跳过

            verify(mockJedis, times(1)).expire(anyString(), anyLong());
        }
    }

    @Test
    public void testRenewPrivateChannel_AllowsRenewAfter10Seconds() throws Exception {
        setRunning(true);
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            mockedTaskExecutor.when(() -> TaskExecutor.submit(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    });

            redisMesh.refreshPrivateChannel();

            java.lang.reflect.Field field = RedisMesh.class.getDeclaredField("lastRefreshTime");
            field.setAccessible(true);
            java.util.concurrent.atomic.AtomicLong lastRefreshTime = (java.util.concurrent.atomic.AtomicLong) field.get(redisMesh);
            lastRefreshTime.set(System.currentTimeMillis() - 11_000);

            redisMesh.refreshPrivateChannel();

            verify(mockJedis, times(2)).expire(anyString(), anyLong());
        }
    }
    @Test
    public void testRefreshPrivateChannel_JedisExceptionDoesNotThrow() throws Exception {
        setRunning(true);
        try (MockedStatic<TaskExecutor> mockedTaskExecutor = mockStatic(TaskExecutor.class)) {
            mockedTaskExecutor.when(() -> TaskExecutor.submit(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    });

            doThrow(new JedisException("Redis error")).when(mockJedis).expire(anyString(), anyLong());

            redisMesh.refreshPrivateChannel(); // 应不抛出异常
        }
    }

    @Test
    public void testMessageListener_ExceptionHandling() {
        RedisMesh.MessageListener listener = new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event e) {
                throw new RuntimeException("Test exception");
            }
        };

        RedisMesh.Event event = RedisMesh.Event.builder().name("test").build();

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {
            assertThrows(RuntimeException.class, () -> {
                try {
                    java.lang.reflect.Method method = RedisMesh.MessageListener.class.getDeclaredMethod("onMessage", RedisMesh.Event.class);
                    method.setAccessible(true);
                    method.invoke(listener, event);
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    if(cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    throw new RuntimeException(e);
                }
            });

            mockedBellaContext.verify(BellaContext::clearAll);
        }
    }
}
