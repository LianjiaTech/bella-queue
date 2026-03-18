package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.model.asr.AsrTaskDTO;
import com.ke.bella.batch.service.AsrTaskCountUpdater;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

import org.jooq.Table;

import static com.ke.bella.batch.db.repo.AsrShardingRepo.TABLE_NAME;
import static org.jooq.impl.DSL.*;

@Component
@Slf4j
public class AsrTaskRepo {

    @Resource(name = "asrDslContext")
    private DSLContext dslContext;

    @Resource
    private AsrShardingRepo shardingRepo;

    @Resource
    private AsrTaskCountUpdater countUpdater;

    /**
     * 根据 shardingKey 构建带反引号的物理表引用
     * shardingKey = "asr-20260319161222" → `asr_tasks_asr-20260319161222`
     */
    private Table<?> shardTable(String shardingKey) {
        return table(name(DSLContextHolder.targetTableName(TABLE_NAME, shardingKey)));
    }

    public AsrTaskDTO saveTask(String queueTaskId, Map<String, Object> taskData) {
        String shardingKey = shardingRepo.findTargetSharding(queueTaskId);

        dslContext.insertInto(shardTable(shardingKey))
                .set(field("task_id"), taskData.get("taskId"))
                .set(field("ak_code"), taskData.get("akCode"))
                .set(field("user"), taskData.get("user"))
                .set(field("model"), taskData.get("model"))
                .set(field("status"), taskData.get("status"))
                .set(field("input_data"), taskData.get("inputData"))
                .set(field("input_file_id"), taskData.get("inputFileId"))
                .set(field("callback_url"), taskData.get("callbackUrl"))
                .execute();

        countUpdater.increase(shardingKey);

        log.info("Task saved - task_id={}, sharding_key={}", taskData.get("taskId"), shardingKey);
        return findByTaskId((String) taskData.get("taskId"));
    }

    public AsrTaskDTO findByTaskId(String taskId) {
        String shardingKey = shardingRepo.findTargetSharding(taskId);

        var result = dslContext.selectFrom(shardTable(shardingKey))
                .where(field("task_id").eq(taskId))
                .fetch();

        if (result.isEmpty()) {
            return null;
        }
        return mapRecordToDTO(result.get(0));
    }

    public List<AsrTaskDTO> findByTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<String>> shardGroups = new HashMap<>();
        for (String taskId : taskIds) {
            String shardingKey = shardingRepo.findTargetSharding(taskId);
            shardGroups.computeIfAbsent(shardingKey, k -> new ArrayList<>()).add(taskId);
        }

        List<AsrTaskDTO> allTasks = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : shardGroups.entrySet()) {
            var result = dslContext.selectFrom(shardTable(entry.getKey()))
                    .where(field("task_id").in(entry.getValue()))
                    .fetch();
            result.stream().map(this::mapRecordToDTO).forEach(allTasks::add);
        }

        return allTasks;
    }

    @Transactional(transactionManager = "asrTransactionManager", rollbackFor = Exception.class)
    public boolean updateTaskOutput(String taskId, String status, String outputData, String outputFileId) {
        String shardingKey = shardingRepo.findTargetSharding(taskId);

        int rowsAffected = dslContext.update(shardTable(shardingKey))
                .set(field("status"), status)
                .set(field("output_data"), outputData)
                .set(field("output_file_id"), outputFileId)
                .set(field("completed_at"), LocalDateTime.now())
                .set(field("updated_at"), LocalDateTime.now())
                .where(field("task_id").eq(taskId)
                        .and(field("status").notIn("completed", "failed")))
                .execute();

        if (rowsAffected == 0) {
            log.warn("Task update skipped - task_id={}, possibly already completed", taskId);
            return false;
        }

        log.info("Task output updated - task_id={}, status={}", taskId, status);
        return true;
    }

    private AsrTaskDTO mapRecordToDTO(Record record) {
        AsrTaskDTO dto = new AsrTaskDTO();
        dto.setId(record.get("id", Long.class));
        dto.setTaskId(record.get("task_id", String.class));
        dto.setAkCode(record.get("ak_code", String.class));
        dto.setUser(record.get("user", String.class));
        dto.setModel(record.get("model", String.class));
        dto.setStatus(record.get("status", String.class));
        dto.setInputData(record.get("input_data", String.class));
        dto.setInputFileId(record.get("input_file_id", String.class));
        dto.setOutputData(record.get("output_data", String.class));
        dto.setOutputFileId(record.get("output_file_id", String.class));
        dto.setCallbackUrl(record.get("callback_url", String.class));
        dto.setCreatedAt(record.get("created_at", LocalDateTime.class));
        dto.setUpdatedAt(record.get("updated_at", LocalDateTime.class));
        dto.setCompletedAt(record.get("completed_at", LocalDateTime.class));
        return dto;
    }
}
