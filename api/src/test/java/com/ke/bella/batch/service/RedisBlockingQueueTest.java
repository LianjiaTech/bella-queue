package com.ke.bella.batch.service;

import com.ke.bella.batch.enums.QueueLevel;
import com.ke.bella.batch.utils.EncryptUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.theokanning.openai.queue.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RedisBlockingQueueTest {

    @Mock
    private JedisPool mockJedisPool;

    @Mock
    private Jedis mockJedis;

    @Mock
    private Pipeline mockPipeline;

    private RedisBlockingQueue redisQueue;

    private static final String QUEUE_NAME = "test-queue:0";
    private static final Integer CAPACITY = 100;
    private static final String TEST_TASK_ID = "task-123";

    @Before
    public void setUp() {
        when(mockJedisPool.getResource()).thenReturn(mockJedis);
        when(mockJedis.pipelined()).thenReturn(mockPipeline);

        redisQueue = new RedisBlockingQueue(QUEUE_NAME, CAPACITY, mockJedisPool);
    }

    @Test
    public void testConstructor_ValidParameters() {
        RedisBlockingQueue queue = new RedisBlockingQueue(QUEUE_NAME, CAPACITY, mockJedisPool);

        assertEquals(mockJedisPool, queue.getJedisPool());
        assertEquals(QUEUE_NAME + ":metadata:", queue.getTaskMetadataKey());
    }

    @Test
    public void testConstructor_NullCapacity() {
        RedisBlockingQueue queue = new RedisBlockingQueue(QUEUE_NAME, null, mockJedisPool);

        assertEquals(Integer.MAX_VALUE - 0, queue.remainingCapacity());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_ZeroCapacity() {
        new RedisBlockingQueue(QUEUE_NAME, 0, mockJedisPool);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeCapacity() {
        new RedisBlockingQueue(QUEUE_NAME, -1, mockJedisPool);
    }

    @Test
    public void testSize() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);

        int size = redisQueue.size();

        assertEquals(5, size);
        verify(mockJedis).zcard(QUEUE_NAME);
    }

    @Test(expected = RuntimeException.class)
    public void testSize_Exception() {
        when(mockJedis.zcard(QUEUE_NAME)).thenThrow(new RuntimeException("Redis error"));

        redisQueue.size();
    }

    @Test
    public void testIsEmpty_True() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(0L);

        assertTrue(redisQueue.isEmpty());
    }

    @Test
    public void testIsEmpty_False() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(3L);

        assertFalse(redisQueue.isEmpty());
    }

    @Test
    public void testRemainingCapacity() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(30L);

        int remaining = redisQueue.remainingCapacity();

        assertEquals(70, remaining);
    }

    @Test
    public void testOffer_Success() {
        Task task = createTestTask();
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(50L); // Below capacity

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class)) {

            mockedEncryptUtils.when(() -> EncryptUtils.encrypt(anyString())).thenReturn("encrypted-ak");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(1L);

            boolean result = redisQueue.offer(task);

            assertTrue(result);
            assertEquals("encrypted-ak", task.getAk());
        }
    }

    @Test
    public void testOffer_QueueFull() {
        Task task = createTestTask();
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(100L); // At capacity

        boolean result = redisQueue.offer(task);

        assertFalse(result);
    }

    @Test
    public void testOffer_EnqueueFails() {
        Task task = createTestTask();
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(50L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class)) {

            mockedEncryptUtils.when(() -> EncryptUtils.encrypt(anyString())).thenReturn("encrypted-ak");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(0L); // Indicates failure

            boolean result = redisQueue.offer(task);

            assertFalse(result);
        }
    }

    @Test
    public void testAdd_Success() {
        Task task = createTestTask();
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(50L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class)) {

            mockedEncryptUtils.when(() -> EncryptUtils.encrypt(anyString())).thenReturn("encrypted-ak");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(1L);

            boolean result = redisQueue.add(task);

            assertTrue(result);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testAdd_QueueFull() {
        Task task = createTestTask();
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(100L);

        redisQueue.add(task);
    }

    @Test
    public void testPoll_Success() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class);
                MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            List<String> luaResult = Arrays.asList(TEST_TASK_ID, "{\"taskId\":\"task-123\",\"ak\":\"encrypted-ak\"}");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(luaResult);

            Task expectedTask = createTestTask();
            expectedTask.setAk("encrypted-ak"); // Set encrypted ak before parsing
            mockedJsonUtils.when(() -> JsonUtils.fromJson(anyString(), eq(Task.class))).thenReturn(expectedTask);
            mockedEncryptUtils.when(() -> EncryptUtils.decrypt("encrypted-ak")).thenReturn("decrypted-ak");

            Task result = redisQueue.poll();

            assertNotNull(result);
            assertEquals("decrypted-ak", result.getAk());
        }
    }

    @Test
    public void testPoll_EmptyQueue() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(0L);

        Task result = redisQueue.poll();

        assertNull(result);
    }

    @Test
    public void testPoll_NoTaskMetadata() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            List<String> luaResult = Arrays.asList(TEST_TASK_ID, null);
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(luaResult);

            Task result = redisQueue.poll();

            assertNull(result);
        }
    }

    @Test(expected = RuntimeException.class)
    public void testPoll_Exception() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Lua execution failed"));

            redisQueue.poll();
        }
    }

    @Test
    public void testPollWithMinAge_ZeroMeansNoRestriction() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class);
                MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            List<String> luaResult = Arrays.asList(TEST_TASK_ID, "{\"taskId\":\"task-123\",\"ak\":\"encrypted-ak\"}");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(luaResult);
            Task expectedTask = createTestTask();
            mockedJsonUtils.when(() -> JsonUtils.fromJson(anyString(), eq(Task.class))).thenReturn(expectedTask);
            mockedEncryptUtils.when(() -> EncryptUtils.decrypt(anyString())).thenReturn("decrypted-ak");

            redisQueue.poll(0L);

            // minAgeSeconds=0 时 maxScore 应为 0（空字符串），表示不限制
            mockedLuaManager.verify(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(),
                    argThat(args -> {
                        List<String> list = (List<String>) args;
                        return list.size() == 2 && list.get(1).equals("");
                    })));
        }
    }

    @Test
    public void testPollWithMinAge_PositivePassesMaxScore() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(5L);
        long minAgeSeconds = 30L;
        long before = System.currentTimeMillis();

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class);
                MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            List<String> luaResult = Arrays.asList(TEST_TASK_ID, "{\"taskId\":\"task-123\",\"ak\":\"encrypted-ak\"}");
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(), anyList()))
                    .thenReturn(luaResult);
            Task expectedTask = createTestTask();
            mockedJsonUtils.when(() -> JsonUtils.fromJson(anyString(), eq(Task.class))).thenReturn(expectedTask);
            mockedEncryptUtils.when(() -> EncryptUtils.decrypt(anyString())).thenReturn("decrypted-ak");

            redisQueue.poll(minAgeSeconds);

            long after = System.currentTimeMillis();
            mockedLuaManager.verify(() -> LuaManager.execute(eq(mockJedisPool), anyString(), anyString(),
                    argThat(args -> {
                        List<String> list = (List<String>) args;
                        if(list.size() != 2 || list.get(1).isEmpty()) return false;
                        long maxScore = Long.parseLong(list.get(1));
                        // maxScore 应在 [before-30s, after-30s] 区间内
                        return maxScore >= before - minAgeSeconds * 1000
                                && maxScore <= after - minAgeSeconds * 1000;
                    })));
        }
    }

    @Test
    public void testPollWithMinAge_EmptyQueueReturnsNull() {
        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(0L);

        Task result = redisQueue.poll(30L);

        assertNull(result);
    }

    @Test
    public void testRemove_Success() {
        Task task = createTestTask();

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), eq("remove"), anyList()))
                    .thenReturn(null);

            boolean result = redisQueue.remove(task);

            assertTrue(result);
            mockedLuaManager.verify(() -> LuaManager.execute(eq(mockJedisPool), eq("remove"),
                    eq(List.of(QUEUE_NAME, TEST_TASK_ID))));
        }
    }

    @Test
    public void testRemove_Exception() {
        Task task = createTestTask();

        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), eq("remove"), anyList()))
                    .thenThrow(new RuntimeException("Remove failed"));

            boolean result = redisQueue.remove(task);

            assertFalse(result);
        }
    }

    @Test
    public void testRemove_NotTask() {
        boolean result = redisQueue.remove("not a task");

        assertFalse(result);
    }

    @Test
    public void testAddAll_Success() {
        Task task1 = createTestTask();
        Task task2 = createTestTask();
        task2.setTaskId("task-456");
        Collection<Task> tasks = Arrays.asList(task1, task2);

        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(50L);

        try (MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class);
                MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            mockedEncryptUtils.when(() -> EncryptUtils.encrypt(anyString())).thenReturn("encrypted-ak");
            mockedJsonUtils.when(() -> JsonUtils.toJson(any(Task.class))).thenReturn("{\"taskJson\":\"data\"}");

            boolean result = redisQueue.addAll(tasks);

            assertTrue(result);
            verify(mockPipeline, times(2)).setex(anyString(), anyLong(), anyString());
            verify(mockPipeline, times(2)).zadd(eq(QUEUE_NAME), anyDouble(), anyString());
            verify(mockPipeline).expire(eq(QUEUE_NAME), anyLong());
            verify(mockPipeline).sync();
        }
    }

    @Test
    public void testAddAll_ExceedsCapacity() {
        Task task1 = createTestTask();
        Task task2 = createTestTask();
        Collection<Task> tasks = Arrays.asList(task1, task2);

        when(mockJedis.zcard(QUEUE_NAME)).thenReturn(99L); // Only 1 remaining capacity, but 2 tasks

        boolean result = redisQueue.addAll(tasks);

        assertFalse(result);
    }

    @Test
    public void testAddAll_EmptyCollection() {
        Collection<Task> tasks = Arrays.asList();

        boolean result = redisQueue.addAll(tasks);

        assertFalse(result);
    }

    @Test
    public void testClear_Success() {
        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), eq("clear"), anyList()))
                    .thenReturn(null);

            redisQueue.clear();

            mockedLuaManager.verify(() -> LuaManager.execute(eq(mockJedisPool), eq("clear"),
                    eq(List.of(QUEUE_NAME))));
        }
    }

    @Test(expected = RuntimeException.class)
    public void testClear_Exception() {
        try (MockedStatic<LuaManager> mockedLuaManager = mockStatic(LuaManager.class)) {
            mockedLuaManager.when(() -> LuaManager.execute(eq(mockJedisPool), eq("clear"), anyList()))
                    .thenThrow(new RuntimeException("Clear failed"));

            redisQueue.clear();
        }
    }

    @Test
    public void testParseTask() {
        String taskJson = "{\"taskId\":\"task-123\",\"ak\":\"encrypted-ak\"}";
        Task expectedTask = createTestTask();
        expectedTask.setAk("encrypted-ak"); // Set encrypted ak before decryption

        try (MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class);
                MockedStatic<EncryptUtils> mockedEncryptUtils = mockStatic(EncryptUtils.class)) {

            mockedJsonUtils.when(() -> JsonUtils.fromJson(taskJson, Task.class)).thenReturn(expectedTask);
            mockedEncryptUtils.when(() -> EncryptUtils.decrypt("encrypted-ak")).thenReturn("decrypted-ak");

            Task result = redisQueue.parseTask(taskJson);

            assertEquals("decrypted-ak", result.getAk());
        }
    }

    @Test
    public void testGetLuaModule_OnlineQueue() {
        try (MockedStatic<QueueLevel> mockedQueueLevel = mockStatic(QueueLevel.class)) {
            mockedQueueLevel.when(() -> QueueLevel.isOnlineQueue(QUEUE_NAME)).thenReturn(true);

            // Use reflection to call private method
            java.lang.reflect.Method method;
            try {
                method = RedisBlockingQueue.class.getDeclaredMethod("getLuaModule");
                method.setAccessible(true);
                String result = (String) method.invoke(redisQueue);
                assertEquals("online", result);
            } catch (Exception e) {
                fail("Failed to test getLuaModule: " + e.getMessage());
            }
        }
    }

    @Test
    public void testGetLuaModule_OfflineQueue() {
        try (MockedStatic<QueueLevel> mockedQueueLevel = mockStatic(QueueLevel.class)) {
            mockedQueueLevel.when(() -> QueueLevel.isOnlineQueue(QUEUE_NAME)).thenReturn(false);

            // Use reflection to call private method
            java.lang.reflect.Method method;
            try {
                method = RedisBlockingQueue.class.getDeclaredMethod("getLuaModule");
                method.setAccessible(true);
                String result = (String) method.invoke(redisQueue);
                assertEquals("offline", result);
            } catch (Exception e) {
                fail("Failed to test getLuaModule: " + e.getMessage());
            }
        }
    }

    // Test methods that return null or default values
    @Test
    public void testUnsupportedOperations() {
        assertNull(redisQueue.remove());
        assertNull(redisQueue.element());
        assertNull(redisQueue.peek());

        // iterator() has @NotNull annotation but implementation returns null
        // Behavior depends on whether runtime @NotNull checking is enabled
        try {
            Iterator<?> result = redisQueue.iterator();
            // If @NotNull checking is disabled, it returns null
            assertNull("Expected null when @NotNull checking is disabled", result);
        } catch (IllegalStateException e) {
            // If @NotNull checking is enabled, it throws IllegalStateException
            assertTrue("Expected @NotNull error message", e.getMessage().contains("@NotNull method"));
        }

        // toArray(T[] a) has @NotNull annotation but implementation returns null
        // Behavior depends on whether runtime @NotNull checking is enabled
        try {
            Object[] result = redisQueue.toArray(new Object[0]);
            // If @NotNull checking is disabled, it returns null
            assertNull("Expected null when @NotNull checking is disabled", result);
        } catch (IllegalStateException e) {
            // If @NotNull checking is enabled, it throws IllegalStateException
            assertTrue("Expected @NotNull error message", e.getMessage().contains("@NotNull method"));
        }

        assertFalse(redisQueue.contains(new Object()));
        assertFalse(redisQueue.containsAll(Arrays.asList(new Object())));
        assertFalse(redisQueue.removeAll(Arrays.asList(new Object())));
        assertFalse(redisQueue.retainAll(Arrays.asList(new Object())));

        assertEquals(0, redisQueue.toArray().length);
        assertEquals(0, redisQueue.drainTo(Arrays.asList()));
        assertEquals(0, redisQueue.drainTo(Arrays.asList(), 5));
    }

    @Test
    public void testBlockingOperations() throws InterruptedException {
        // These methods are not implemented but should not throw exceptions
        redisQueue.put(createTestTask());
        assertFalse(redisQueue.offer(createTestTask(), 1, java.util.concurrent.TimeUnit.SECONDS));

        // take() has @NotNull annotation but implementation returns null
        // Behavior depends on whether runtime @NotNull checking is enabled
        try {
            Task result = redisQueue.take();
            // If @NotNull checking is disabled, it returns null
            assertNull("Expected null when @NotNull checking is disabled", result);
        } catch (IllegalStateException e) {
            // If @NotNull checking is enabled, it throws IllegalStateException
            assertTrue("Expected @NotNull error message", e.getMessage().contains("@NotNull method"));
        }

        assertNull(redisQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS));
    }

    private Task createTestTask() {
        return Task.builder()
                .taskId(TEST_TASK_ID)
                .ak("test-apikey")
                .startTime(System.currentTimeMillis())
                .build();
    }
}
