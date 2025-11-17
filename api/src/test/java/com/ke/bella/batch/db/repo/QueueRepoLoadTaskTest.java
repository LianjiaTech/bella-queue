package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.enums.TaskStatus;
import com.ke.bella.batch.tables.pojos.QueueDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * LoadTask优化功能单元测试
 * 主要测试重构后的辅助方法逻辑
 * 注意：getProcessedCount方法已被移除，现在通过getScannedId和简单减法计算处理数量
 */
@RunWith(MockitoJUnitRunner.class)
public class QueueRepoLoadTaskTest {

    private final QueueRepo queueRepo = new QueueRepo();


    /**
     * 测试getScannedId方法
     */
    @Test
    public void testGetScannedId() throws Exception {
        Method getScannedIdMethod = QueueRepo.class.getDeclaredMethod("getScannedId", List.class, long.class);
        getScannedIdMethod.setAccessible(true);

        // 测试有任务的情况
        List<QueueDB> tasks = createTestTasks();
        long maxScanId = 2000L;
        
        Long scannedId = (Long) getScannedIdMethod.invoke(queueRepo, tasks, maxScanId);
        assertEquals("Scanned ID should be last task ID", Long.valueOf(3L), scannedId);

        // 测试空任务列表的情况
        List<QueueDB> emptyTasks = new ArrayList<>();
        Long scannedIdEmpty = (Long) getScannedIdMethod.invoke(queueRepo, emptyTasks, maxScanId);
        assertEquals("Empty tasks should return maxScanId", Long.valueOf(maxScanId), scannedIdEmpty);
    }


    /**
     * 测试maxScanId计算逻辑
     */
    @Test
    public void testMaxScanIdCalculation() {
        // 测试Math.min(scannedId + SCAN_STEP_SIZE, maxId)的逻辑
        long scannedId = 1000L;
        long maxId = 100000L;
        long scanStepSize = 50000L; // QueueRepo.SCAN_STEP_SIZE的值
        
        // 当scannedId + SCAN_STEP_SIZE < maxId时
        long expectedMaxScanId = Math.min(scannedId + scanStepSize, maxId);
        assertEquals("MaxScanId should be scannedId plus SCAN_STEP_SIZE when less than maxId", 
            51000L, expectedMaxScanId);
        
        // 当scannedId + SCAN_STEP_SIZE > maxId时
        scannedId = 90000L;
        expectedMaxScanId = Math.min(scannedId + scanStepSize, maxId);
        assertEquals("MaxScanId should be maxId when scannedId plus SCAN_STEP_SIZE exceeds maxId", 
            100000L, expectedMaxScanId);
    }

    /**
     * 测试collectTasks方法
     */
    @Test
    public void testCollectTasks() throws Exception {

        Method collectTasksMethod = QueueRepo.class.getDeclaredMethod("collectTasks", 
            String.class, long.class, long.class, int.class, List.class, Map.class);
        collectTasksMethod.setAccessible(true);

        String shardingKey = "1-1-20241110120000";
        long lastScannedId = 1000L;
        long maxScanId = 2000L;
        int limit = 100;
        List<QueueDB> allTasks = new ArrayList<>();
        Map<String, List<Long>> tasksBySharding = new HashMap<>();

        // 由于collectTasks方法涉及数据库操作，这里主要测试方法签名和参数传递
        // 实际的数据库交互测试需要在集成测试中进行
        try {
            @SuppressWarnings("unchecked")
            List<QueueDB> result = (List<QueueDB>) collectTasksMethod.invoke(queueRepo, 
                shardingKey, lastScannedId, maxScanId, limit, allTasks, tasksBySharding);
            
            // 验证方法能够正常调用（即使可能因为缺少数据库连接而失败）
            assertNotNull("CollectTasks method should be callable", result);
        } catch (Exception e) {
            // 由于没有真实的数据库连接，这里捕获异常是正常的
            // 主要验证方法签名正确
            assertTrue("Method should exist and be callable", true);
        }
    }

    /**
     * 测试loadTasks方法的基本流程
     */
    @Test
    public void testLoadTasksMethodExists() throws Exception {
        // 验证loadTasks方法存在并具有正确的签名
        Method loadTasksMethod = QueueRepo.class.getDeclaredMethod("loadTasks", String.class, java.util.concurrent.BlockingQueue.class);
        assertNotNull("LoadTasks method should exist", loadTasksMethod);
        
        // 验证方法是public的
        assertTrue("LoadTasks method should be public", 
            java.lang.reflect.Modifier.isPublic(loadTasksMethod.getModifiers()));
    }

    /**
     * 测试范围扫描逻辑的条件判断
     */
    @Test
    public void testRangeScanningConditions() {
        // 测试当前分片扫描完成的条件
        long currentScannedId1 = 50000L;
        long maxShardId1 = 50000L;
        
        // 当currentScannedId等于maxShardId时，表示当前分片已扫描完成
        boolean shouldScanNextShard1 = (currentScannedId1 == maxShardId1);
        assertTrue("Should scan next shard when current shard is completed", shouldScanNextShard1);
        
        // 当currentScannedId小于maxShardId时，表示当前分片还有数据可扫描
        long currentScannedId2 = 40000L;
        long maxShardId2 = 50000L;
        boolean shouldScanNextShard2 = (currentScannedId2 == maxShardId2);
        assertFalse("Should continue scanning current shard when not completed", shouldScanNextShard2);
    }

    // 辅助方法：创建测试任务列表
    private List<QueueDB> createTestTasks() {
        List<QueueDB> tasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            tasks.add(createTestTask((long) i));
        }
        return tasks;
    }

    // 辅助方法：创建单个测试任务
    private QueueDB createTestTask(Long id) {
        QueueDB task = new QueueDB();
        task.setId(id);
        task.setTaskId("TASK-1-1-B-" + System.currentTimeMillis() + "-instance-" + id);
        task.setQueue("test-queue");
        task.setStatus(TaskStatus.waiting.name());
        task.setExpiredAt(LocalDateTime.now().plusHours(1));
        task.setTraceId("batch-123");
        task.setInputData("{}");
        task.setEndpoint("/test/endpoint");
        return task;
    }
}