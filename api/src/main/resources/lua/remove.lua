local queueName = ARGV[1]
local taskId = ARGV[2]

-- 构建任务元数据key
local taskKey = queueName .. ":metadata:" .. taskId

-- 从队列中移除任务
redis.call('ZREM', queueName, taskId)

-- 删除任务元数据
redis.call('DEL', taskKey)

return 1
