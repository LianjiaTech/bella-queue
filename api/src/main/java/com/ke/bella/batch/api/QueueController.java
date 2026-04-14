package com.ke.bella.batch.api;

import com.ke.bella.batch.api.protocol.BellaResponse;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.ResponseMode;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.service.callback.BlockingCallback;
import com.ke.bella.batch.service.callback.StreamingCallback;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.ke.bella.batch.tables.pojos.QueueHeadDB;
import com.theokanning.openai.queue.EventbusConfig;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Register;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/queue")
public class QueueController {

    @Resource
    private QueueService qs;
    @Resource
    private QueueRepo queueRepo;

    @PostMapping("/register")
    public String register(@RequestBody Register register) {
        Assert.notNull(register, "request cannot be null");
        Assert.isTrue(StringUtils.hasText(register.getEndpoint()), "endpoint cannot be empty");
        Assert.isTrue(StringUtils.hasText(register.getQueue()), "queue cannot be empty");
        Assert.isTrue(register.getQueue().matches("^[a-zA-Z0-9_\\-]+$"),
                "queue name can only contain alphanumeric characters, underscores, and hyphens");
        Assert.isTrue(register.getQueue().length() <= 64, "queue name cannot exceed 64 characters");

        queueRepo.register(register);
        return register.getQueue();
    }

    @GetMapping("/eventbus")
    public EventbusConfig getEventbusConfig() {
        return qs.getEventbusConfig();
    }

    @GetMapping("/{task_id}")
    public Task getTask(@PathVariable("task_id") String taskId) {
        Assert.notNull(taskId, "taskId cannot be null");
        QueueDB queueDB = queueRepo.findTask(taskId);
        return queueRepo.parseTask(queueDB);
    }

    @PostMapping("/put")
    public Object put(@RequestBody Put put) {
        Assert.notNull(put, "put request cannot be null");
        Assert.notNull(put.getEndpoint(), "endpoint cannot be null");
        Assert.notNull(put.getData(), "data cannot be null");

        log.info("put request: endpoint={}, queue={}, responseMode={}",
                put.getEndpoint(), put.getQueue(), put.getResponseMode());

        Task task = qs.put(put);

        log.info("put response: taskId={}, responseMode={}",
                task.getTaskId(), put.getResponseMode());

        if(ResponseMode.blocking.name().equals(put.getResponseMode())) {
            BlockingCallback callback = new BlockingCallback(task.getTaskId(), qs, put.getTimeout());
            qs.registerTaskCallback(task.getTaskId(), callback);
            Map<String, Object> result = callback.getResult();
            Integer statusCode = MapUtils.getInteger(result, BlockingCallback.STATUS_CODE, 200);
            Object payload = MapUtils.getObject(result, BlockingCallback.BODY);
            return ResponseEntity.status(statusCode).body(payload);
        } else if(ResponseMode.streaming.name().equals(put.getResponseMode())) {
            StreamingCallback callback = new StreamingCallback(task.getTaskId(), qs, put.getTimeout());
            qs.registerTaskCallback(task.getTaskId(), callback);
            return callback.getEmitter();
        } else {
            return BellaResponse.<String>builder()
                    .code(200)
                    .data(task.getTaskId())
                    .build();
        }
    }

    @PostMapping("/take")
    public Map<String, List<Task>> take(@RequestBody Take take) {
        Assert.notNull(take, "take parameter cannot be null");
        Assert.notEmpty(take.getQueues(), "queues cannot be empty");

        return qs.take(take);
    }

    @PostMapping("{taskId}/cancel")
    @ResponseStatus(code = HttpStatus.ACCEPTED)
    public String cancel(@PathVariable String taskId) {
        Assert.notNull(taskId, "taskId cannot be null");

        qs.cancel(taskId);
        return taskId;
    }

    @PostMapping("/{taskId}/complete")
    public String complete(@PathVariable String taskId, @RequestBody Map<String, Object> data) {
        Assert.notNull(taskId, "taskId cannot be null");

        qs.complete(taskId, data);
        return taskId;
    }

    @GetMapping("/{fullQueueName}/stats")
    public QueueHeadDB getQueueStats(@PathVariable String fullQueueName) {
        Assert.hasText(fullQueueName, "fullQueueName cannot be null or empty");

        return queueRepo.findQueueHead(fullQueueName);
    }

}
