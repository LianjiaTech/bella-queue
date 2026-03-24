local queueName = ARGV[1]
local maxScore = ARGV[2]

-- 如果指定了 maxScore，先检查最小 score 的元素是否满足条件
if maxScore and maxScore ~= "" then
    local peek = redis.call('ZRANGEBYSCORE', queueName, '-inf', maxScore, 'LIMIT', 0, 1)
    if #peek == 0 then
        return nil
    end
end

-- 从队列中取出优先级最高的任务
local result = redis.call('ZPOPMIN', queueName, 1)
if #result == 0 then
    return nil
end

-- 获取任务ID和元数据
local taskId = result[1]
local taskKey = queueName .. ":metadata:" .. taskId

-- 先获取任务元数据
local taskJson = redis.call('GET', taskKey)
if not taskJson then
    return nil
end

-- 解析任务数据，检查responseMode
local taskData = cjson.decode(taskJson)
local responseMode = taskData.response_mode

-- 如果不是callback模式，则删除任务元数据
if responseMode ~= "callback" then
    redis.call('DEL', taskKey)
end

-- 返回任务数据
return {taskId, taskJson}
