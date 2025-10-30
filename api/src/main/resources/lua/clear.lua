local queueName = ARGV[1]

-- 获取所有任务ID
local taskIds = redis.call('ZRANGE', queueName, 0, -1)

-- 删除所有任务元数据
for i = 1, #taskIds do
    local taskKey = queueName .. ":metadata:" .. taskIds[i]
    redis.call('DEL', taskKey)
end

-- 删除队列
redis.call('DEL', queueName)

return 1