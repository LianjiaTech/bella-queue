package com.ke.bella.batch.api;

import com.ke.bella.batch.db.repo.BatchRepo;
import com.ke.bella.batch.enums.CompletionWindow;
import com.ke.bella.batch.service.BatchService;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/v1/batches")
public class BatchController {

    @Resource
    private BatchService bs;
    @Resource
    private BatchRepo batchRepo;

    @PostMapping
    public Batch create(@RequestBody BatchRequest create, @RequestHeader(value = "X-BELLA-QUEUE-NAME", required = false) String queue) {
        Assert.hasText(create.getCompletionWindow(), "completion window cannot be null or empty");
        Assert.hasText(create.getInputFileId(), "input file id cannot be null or empty");
        Assert.hasText(create.getEndpoint(), "endpoint cannot be null or empty");

        if(CompletionWindow.isNotValid(create.getCompletionWindow())) {
            create.setCompletionWindow(CompletionWindow.DEFAULT);
        }

        return bs.create(create, queue);
    }

    @GetMapping("/{batch_id}")
    public Batch retrieve(@PathVariable("batch_id") String batchId) {
        Assert.hasText(batchId, "batch_id cannot be null or empty");

        return batchRepo.findBatch(batchId);
    }

    @PostMapping("/{batch_id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Batch cancel(@PathVariable("batch_id") String batchId) {
        Assert.hasText(batchId, "batch_id cannot be null or empty");

        return bs.cancel(batchId);
    }

    @GetMapping
    public OpenAiResponse<Batch> list(@ModelAttribute ListSearchParameters retrieve) {
        Assert.isTrue(retrieve.getLimit() > 0, "Limit must be greater than 0");

        List<Batch> allBatches = batchRepo.findBatches(retrieve.getAfter()
                , retrieve.getLimit() + 1);

        if(allBatches.isEmpty()) {
            return new OpenAiResponse<>();
        }

        boolean hasMore = allBatches.size() > retrieve.getLimit();
        List<Batch> data = hasMore ? allBatches.subList(0, retrieve.getLimit()) : allBatches;

        OpenAiResponse<Batch> response = new OpenAiResponse<>();
        response.setObject("list");
        response.setHasMore(hasMore);
        response.setData(data);

        return response;
    }
}
