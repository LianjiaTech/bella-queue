package com.ke.bella.batch.api;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.service.QueueHeadUpdater;
import com.ke.bella.batch.tables.pojos.QueueHeadDB;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/v1/console")
public class ConsoleController {

    @Resource
    private QueueService queueService;
    @Resource
    private QueueRepo queueRepo;
    @Resource
    private QueueHeadUpdater queueHeadUpdater;

    @PostMapping("/{fullQueueName}/clear")
    public String flushHeadToLatest(@PathVariable String fullQueueName) {
        Assert.hasText(fullQueueName, "fullQueueName cannot be null or empty");

        queueHeadUpdater.flushStatsForQueue(fullQueueName);
        queueRepo.moveScanHeadToLatest(fullQueueName);
        queueService.getQueue(fullQueueName).clear();

        return fullQueueName;
    }

    @GetMapping("/queue/stats")
    public List<QueueHeadDB> getAllQueueStats() {
        return queueRepo.findAllQueueHeads();
    }
}
