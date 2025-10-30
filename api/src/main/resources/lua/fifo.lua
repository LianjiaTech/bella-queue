-- FIFO策略脚本: 从多个队列中按时间戳优先级取出任务
-- 参数结构: ARGV[1]=maxSize, ARGV[2]=numQueues, 
--          ARGV[3..2+numQueues]=队列名

local tasks = {}
local maxSize = tonumber(ARGV[1])
local numQueues = tonumber(ARGV[2])
local maxRetries = maxSize * numQueues * 3  -- 最大轮询次数

local count = 0
local retryCount = 0
while count < maxSize and retryCount < maxRetries do
    retryCount = retryCount + 1
    -- 查找具有最早任务的队列(最小时间戳)
    local earliestQueue = nil
    local earliestTimestamp = nil
    local earliestTaskId = nil
    local earliestMetadataKey = nil
    local earliestQueueIndex = nil
    
    -- 遍历所有队列，找到时间戳最小的任务
    for i = 1, numQueues do
        local queueKey = ARGV[2 + i]
        local metadataKey = queueKey .. ":metadata:"
        
        -- 获取当前队列中最早的任务
        local queueHead = redis.call('ZRANGE', queueKey, 0, 0, 'WITHSCORES')
        
        if #queueHead >= 2 then
            local taskId = queueHead[1]
            local timestamp = tonumber(queueHead[2])
            
            -- 检查是否为目前最早的任务
            if earliestTimestamp == nil or timestamp < earliestTimestamp then
                earliestQueue = queueKey
                earliestTimestamp = timestamp
                earliestTaskId = taskId
                earliestMetadataKey = metadataKey
                earliestQueueIndex = i
            end
        end
    end
    
    -- 如果所有队列都没有任务，退出循环
    if earliestQueue == nil then
        break
    end
    
    -- 尝试移除最早的任务
    local removed = redis.call('ZREM', earliestQueue, earliestTaskId)
    if removed == 1 then
        -- 成功移除，获取任务元数据
        local taskKey = earliestMetadataKey .. earliestTaskId
        local taskJson = redis.call('GET', taskKey)
        
        -- 清理任务元数据
        redis.call('DEL', taskKey)
        
        -- 任务成功移除时总是增加计数
        count = count + 1
        
        -- 只有元数据存在时才添加到结果中
        if taskJson then
            table.insert(tasks, {earliestTaskId, taskJson})
        end
    end
    -- 如果任务已被其他线程获取，继续循环
    -- 这种方式自然处理并发，无需额外逻辑
end

return tasks
