local queueName = ARGV[1]
local taskId = ARGV[2]
local taskMetadata = ARGV[3]
local taskStartTime = tonumber(ARGV[4])
local capacity = tonumber(ARGV[5])

-- 存储任务元数据和添加到队列
local taskKey = queueName .. ":metadata:" .. taskId
redis.call('SETEX', taskKey, 60 * 10, taskMetadata)
redis.call('ZADD', queueName, taskStartTime, taskId)

-- 设置队列过期时间
redis.call('EXPIRE', queueName, 60 * 60 * 24)

return 1
