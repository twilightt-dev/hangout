-- 每个分区一次读取；缺失或损坏只影响对应评论。
local result = {}
for i = 1, #ARGV / 2 do
    local relation = KEYS[i * 2 - 1]
    local meta = KEYS[i * 2]
    local relationType = redis.call('TYPE', relation).ok
    local metaType = redis.call('TYPE', meta).ok
    if (relationType == 'none' or relationType == 'set') and metaType == 'hash'
        and redis.call('HGET', meta, 'epoch') == ARGV[i * 2 - 1]
        and tonumber(redis.call('HGET', meta, 'version'))
        and tonumber(redis.call('HGET', meta, 'count')) == redis.call('SCARD', relation) then
        result[#result + 1] = 1
        result[#result + 1] = ARGV[i * 2] == '' and 0 or redis.call('SISMEMBER', relation, ARGV[i * 2])
    else result[#result + 1] = 0; result[#result + 1] = 0 end
end
return result
