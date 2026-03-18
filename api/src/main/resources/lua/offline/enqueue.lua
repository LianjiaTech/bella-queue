local queueName = ARGV[1]
local taskId = ARGV[2]
local taskMetadata = ARGV[3]
local taskStartTime = tonumber(ARGV[4])
local capacity = tonumber(ARGV[5])
local ttl = tonumber(ARGV[6])

-- 存储任务元数据和添加到队列
local taskKey = queueName .. ":metadata:" .. taskId
redis.call('SETEX', taskKey, ttl, taskMetadata)
redis.call('ZADD', queueName, taskStartTime, taskId)
redis.call('EXPIRE', queueName, ttl)

return 1
