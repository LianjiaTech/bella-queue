package com.ke.bella.batch;

import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.log4j.MDC;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TaskExecutor {
    static ThreadFactory tf = new NamedThreadFactory("bella-queue-", true);
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(1000, tf);

    static ThreadFactory batchTf = new NamedThreadFactory("bella-batch-splitting-", true);
    static ScheduledExecutorService batchExecutor = Executors.newScheduledThreadPool(Configs.BATCH_THREAD_SIZE, batchTf);

    static ThreadFactory scheduledTf = new NamedThreadFactory("bella-batch-scheduled-", true);
    static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(100, scheduledTf);

    static ThreadFactory usageTf = new NamedThreadFactory("bella-usage-scheduled-", true);
    static ScheduledExecutorService usageExecutor = Executors.newScheduledThreadPool(50, usageTf);

    public static void scheduleAtFixedRate(Runnable r, int period) {
        int initialDelay = period + RandomUtils.nextInt(1, period);
        scheduledExecutor.scheduleAtFixedRate(new Task(r), initialDelay, period, TimeUnit.SECONDS);
    }

    public static void submit(Runnable r) {
        executor.submit(new Task(r));
    }

    public static void submitUsage(Runnable r) {
        usageExecutor.submit(new Task(r));
    }

    public static void submitBatch(Runnable r) {
        batchExecutor.submit(new Task(r));
    }

    public static void gracefulShutdown(long timeoutSeconds) throws InterruptedException {
        log.info("Starting TaskExecutor graceful shutdown...");

        executor.shutdown();
        batchExecutor.shutdown();
        scheduledExecutor.shutdown();
        usageExecutor.shutdown();

        CompletableFuture<Boolean> executorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });

        boolean batchExecutorTerminated = batchExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        boolean scheduledExecutorTerminated = scheduledExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        boolean usageExecutorTerminated = usageExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);

        boolean executorTerminated;
        try {
            executorTerminated = executorFuture.get();
        } catch (ExecutionException e) {
            log.warn("Error waiting for executor termination", e);
            executorTerminated = false;
        }

        if(!executorTerminated) {
            log.warn("General executor did not terminate gracefully, forcing shutdown");
            executor.shutdownNow();
        }

        if(!batchExecutorTerminated) {
            log.warn("Batch executor did not terminate gracefully, forcing shutdown");
            batchExecutor.shutdownNow();
        }

        if(!scheduledExecutorTerminated) {
            log.warn("Scheduled executor did not terminate gracefully, forcing shutdown");
            scheduledExecutor.shutdownNow();
        }

        if(!usageExecutorTerminated) {
            log.warn("Usage executor did not terminate gracefully, forcing shutdown");
            usageExecutor.shutdownNow();
        }

        log.info("TaskExecutor shutdown completed - executor: {}, batchExecutor: {}, scheduledExecutor: {}, usageExecutor: {}",
                executorTerminated ? "graceful" : "forced",
                batchExecutorTerminated ? "graceful" : "forced",
                scheduledExecutorTerminated ? "graceful" : "forced",
                usageExecutorTerminated ? "graceful" : "forced");
    }

    public static class Task implements Runnable {
        Runnable r;

        public Task(Runnable r) {
            this.r = wrapWithContext(r);
        }

        @Override
        public void run() {
            r.run();
        }

        private Runnable wrapWithContext(Runnable r) {
            Map<String, Object> context = BellaContext.snapshot();
            return () -> {
                BellaContext.replace(context);
                String bellaTraceId = BellaContext.getTraceId();
                if(bellaTraceId != null) {
                    MDC.put(BellaContext.BELLA_TRACE_HEADER, bellaTraceId);
                }
                try {
                    r.run();
                } catch (Exception e) {
                    log.error("Scheduled task execution failed", e);
                } finally {
                    BellaContext.clearAll();
                    MDC.clear();
                }
            };
        }
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean isDaemon;
        private final Thread.UncaughtExceptionHandler handler;

        public NamedThreadFactory(String prefix, boolean isDaemon) {
            this(prefix, isDaemon, null);
        }

        public NamedThreadFactory(String prefix, boolean isDaemon, Thread.UncaughtExceptionHandler handler) {
            this.prefix = prefix;
            this.isDaemon = isDaemon;
            this.handler = handler;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            final Thread t = new Thread(r, String.format("%s%d", prefix, threadNumber.getAndIncrement()));
            t.setDaemon(isDaemon);
            if(this.handler != null) {
                t.setUncaughtExceptionHandler(handler);
            }
            return t;
        }
    }
}
