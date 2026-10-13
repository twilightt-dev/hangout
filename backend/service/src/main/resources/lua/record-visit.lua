-- 先读取统计，损坏的键不能消耗访问事件；重试已成功事件不占用频率额度。
local count
if ARGV[1] == 'pv' then
    count = redis.call('GET', KEYS[1]) or '0'
    if not string.match(count, '^%d+$') then
        return redis.error_reply('Invalid PV total')
    end
else
    count = redis.call('PFCOUNT', KEYS[1])
end
if redis.call('EXISTS', KEYS[2]) == 1 then
    return tonumber(count)
end

local attempts = redis.call('INCR', KEYS[3])
if attempts == 1 then
    redis.call('EXPIRE', KEYS[3], ARGV[4])
end
if attempts > tonumber(ARGV[3]) then
    return -1
end

if ARGV[1] == 'pv' then
    count = redis.call('INCR', KEYS[1])
else
    redis.call('PFADD', KEYS[1], ARGV[2])
    count = redis.call('PFCOUNT', KEYS[1])
end
redis.call('SET', KEYS[2], '1', 'EX', ARGV[5])
return tonumber(count)
