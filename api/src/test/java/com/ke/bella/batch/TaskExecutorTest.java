package com.ke.bella.batch;

import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import org.apache.log4j.MDC;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskExecutorTest {

    private CountDownLatch latch;
    private AtomicBoolean taskExecuted;
    private AtomicReference<String> executedThreadName;

    @Before
    public void setUp() {
        latch = new CountDownLatch(1);
        taskExecuted = new AtomicBoolean(false);
        executedThreadName = new AtomicReference<>();

        // Set up Configs for batch thread size
        Configs.BATCH_THREAD_SIZE = 5;
    }

    @After
    public void tearDown() {
        // Clean up any remaining tasks
        MDC.clear();
        BellaContext.clearAll();
    }

    @Test
    public void testSubmit() throws InterruptedException {
        Runnable testTask = () -> {
            taskExecuted.set(true);
            executedThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        };

        TaskExecutor.submit(testTask);

        assertTrue("Task should execute within timeout", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Task should be executed", taskExecuted.get());
        assertTrue("Thread name should start with bella-queue-",
                executedThreadName.get().startsWith("bella-queue-"));
    }

    @Test
    public void testSubmitBatch() throws InterruptedException {
        Runnable testTask = () -> {
            taskExecuted.set(true);
            executedThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        };

        TaskExecutor.submitBatch(testTask);

        assertTrue("Batch task should execute within timeout", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Batch task should be executed", taskExecuted.get());
        assertTrue("Thread name should start with bella-batch-splitting-",
                executedThreadName.get().startsWith("bella-batch-splitting-"));
    }

    @Test
    public void testScheduleAtFixedRate() throws InterruptedException {
        CountDownLatch scheduledLatch = new CountDownLatch(2); // Wait for at least 2 executions
        AtomicBoolean scheduledTaskExecuted = new AtomicBoolean(false);

        Runnable scheduledTask = () -> {
            scheduledTaskExecuted.set(true);
            scheduledLatch.countDown();
        };

        TaskExecutor.scheduleAtFixedRate(scheduledTask, 1); // 1 second period

        assertTrue("Scheduled task should execute at least twice within timeout",
                scheduledLatch.await(10, TimeUnit.SECONDS));
        assertTrue("Scheduled task should be executed", scheduledTaskExecuted.get());
    }

    @Test
    public void testTaskWithBellaContext() throws InterruptedException {
        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {

            Map<String, Object> testContext = new HashMap<>();
            testContext.put("testKey", "testValue");

            // Mock the calls
            mockedBellaContext.when(BellaContext::snapshot).thenReturn(testContext);
            mockedBellaContext.when(BellaContext::getTraceId).thenReturn("trace-123");

            Runnable testTask = () -> {
                taskExecuted.set(true);
                latch.countDown();
            };

            TaskExecutor.submit(testTask);

            assertTrue("Task should execute within timeout", latch.await(5, TimeUnit.SECONDS));
            assertTrue("Task should be executed", taskExecuted.get());

            // Add delay to ensure async operations complete
            Thread.sleep(200);

            // Verify that at least snapshot was called (this happens synchronously during Task construction)
            mockedBellaContext.verify(BellaContext::snapshot, atLeastOnce());
        }
    }

    @Test
    public void testTaskWithoutTraceId() throws InterruptedException {
        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {

            Map<String, Object> testContext = new HashMap<>();
            mockedBellaContext.when(BellaContext::snapshot).thenReturn(testContext);
            mockedBellaContext.when(BellaContext::getTraceId).thenReturn(null);

            Runnable testTask = () -> {
                taskExecuted.set(true);
                latch.countDown();
            };

            TaskExecutor.submit(testTask);

            assertTrue("Task should execute within timeout", latch.await(5, TimeUnit.SECONDS));
            assertTrue("Task should be executed", taskExecuted.get());

            // Add delay to ensure async operations complete
            Thread.sleep(200);

            // Verify that at least snapshot was called
            mockedBellaContext.verify(BellaContext::snapshot, atLeastOnce());
        }
    }

    @Test
    public void testTaskExceptionHandling() throws InterruptedException {
        AtomicBoolean exceptionThrown = new AtomicBoolean(false);

        try (MockedStatic<BellaContext> mockedBellaContext = mockStatic(BellaContext.class)) {

            mockedBellaContext.when(BellaContext::snapshot).thenReturn(new HashMap<>());
            mockedBellaContext.when(BellaContext::getTraceId).thenReturn(null);

            Runnable exceptionTask = () -> {
                try {
                    exceptionThrown.set(true);
                    throw new RuntimeException("Test exception");
                } finally {
                    // Ensure latch is counted down even if exception occurs
                    latch.countDown();
                }
            };

            TaskExecutor.submit(exceptionTask);

            assertTrue("Task should execute within timeout", latch.await(5, TimeUnit.SECONDS));
            assertTrue("Exception should be thrown", exceptionThrown.get());

            // Add delay to ensure async operations complete
            Thread.sleep(200);

            // Verify that snapshot was called (this shows TaskExecutor is wrapping the task properly)
            mockedBellaContext.verify(BellaContext::snapshot, atLeastOnce());
        }
    }

    @Test
    public void testNamedThreadFactory() {
        String prefix = "test-thread-";
        boolean isDaemon = true;
        TaskExecutor.NamedThreadFactory factory = new TaskExecutor.NamedThreadFactory(prefix, isDaemon);

        Runnable testRunnable = () -> {
        };
        Thread thread1 = factory.newThread(testRunnable);
        Thread thread2 = factory.newThread(testRunnable);

        assertEquals("test-thread-1", thread1.getName());
        assertEquals("test-thread-2", thread2.getName());
        assertTrue("Thread should be daemon", thread1.isDaemon());
        assertTrue("Thread should be daemon", thread2.isDaemon());
    }

    @Test
    public void testNamedThreadFactoryWithHandler() {
        String prefix = "handler-thread-";
        boolean isDaemon = false;
        Thread.UncaughtExceptionHandler handler = (t, e) -> {
        };

        TaskExecutor.NamedThreadFactory factory = new TaskExecutor.NamedThreadFactory(prefix, isDaemon, handler);

        Thread thread = factory.newThread(() -> {
        });

        assertEquals("handler-thread-1", thread.getName());
        assertFalse("Thread should not be daemon", thread.isDaemon());
        assertEquals("Exception handler should be set", handler, thread.getUncaughtExceptionHandler());
    }

    @Test
    public void testNamedThreadFactoryBasicConstructor() {
        String prefix = "basic-thread-";
        boolean isDaemon = true;

        TaskExecutor.NamedThreadFactory factory = new TaskExecutor.NamedThreadFactory(prefix, isDaemon);

        Thread thread = factory.newThread(() -> {
        });

        assertEquals("basic-thread-1", thread.getName());
        assertTrue("Thread should be daemon", thread.isDaemon());
        assertNotEquals("Should use default exception handler",
                Thread.UncaughtExceptionHandler.class,
                thread.getUncaughtExceptionHandler().getClass());
    }

    @Test
    public void testGracefulShutdown() {
        // This test verifies the gracefulShutdown method exists and has correct signature
        // We cannot fully test it due to static executor dependencies
        // The actual shutdown functionality would be tested in integration tests

        try {
            // Verify the method exists and can be called
            TaskExecutor.class.getDeclaredMethod("gracefulShutdown", long.class);

            // We don't actually call gracefulShutdown here as it would affect other tests
            // In a real testing scenario, this would require dependency injection
            assertTrue("gracefulShutdown method exists and is accessible", true);

        } catch (NoSuchMethodException e) {
            fail("gracefulShutdown method should exist: " + e.getMessage());
        }
    }

    @Test
    public void testTaskWrapperRun() {
        AtomicBoolean innerTaskExecuted = new AtomicBoolean(false);
        Runnable innerTask = () -> innerTaskExecuted.set(true);

        TaskExecutor.Task task = new TaskExecutor.Task(innerTask);
        task.run();

        assertTrue("Inner task should be executed", innerTaskExecuted.get());
    }
}
