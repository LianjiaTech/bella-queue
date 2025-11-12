-- sequential.lua
-- 多队列顺序执行策略：每个队列取一个任务
-- ARGV: queueName1, queueName2, ...

local function processQueue(queueName)
    local runningKey = "sequential:running:" .. queueName
    
    -- 跳过正在运行的队列
    if redis.call('EXISTS', runningKey) == 1 then
        return nil
    end
    
    -- 获取最早任务
    local task = redis.call('ZPOPMIN', queueName)
    if not task or #task == 0 then
        return nil
    end
    
    local taskId = task[1]
    local metadataKey = queueName .. ':metadata:' .. taskId
    
    -- 获取任务数据
    local taskData = redis.call('GETDEL', metadataKey)
    if not taskData then
        return nil
    end
    
    -- 设置运行锁并返回结果
    redis.call('SETEX', runningKey, 600, taskId)
    return taskData
end

local results = {}
for i = 1, #ARGV do
    local result = processQueue(ARGV[i])
    if result then
        table.insert(results, result)
    end
end

return results
