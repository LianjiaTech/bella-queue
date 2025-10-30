local queueName = ARGV[1]
local taskId = ARGV[2]
local taskMetadata = ARGV[3]
local taskStartTime = tonumber(ARGV[4])
local capacity = tonumber(ARGV[5])

-- 存储任务元数据和添加到队列
local taskKey = queueName .. ":metadata:" .. taskId
redis.call('SET', taskKey, taskMetadata)
redis.call('ZADD', queueName, taskStartTime, taskId)

return 1
