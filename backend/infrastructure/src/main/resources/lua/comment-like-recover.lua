-- 仅供已审核的离线逐条恢复：必须先停止全部写入与同步，不由应用自动调用。
-- 参数：预期Redis代次或missing、旧MySQL代次、新代次、审核数量、评论ID。
local function validType(key, expected)
    local t = redis.call('TYPE', key).ok
    return t == 'none' or t == expected
end
if not validType(KEYS[1], 'set') or not validType(KEYS[2], 'hash') or not validType(KEYS[3], 'zset') then return {-1} end
local old = redis.call('HGET', KEYS[2], 'epoch')
if (old or 'missing') ~= ARGV[1] then return {-1} end
local new = tonumber(ARGV[3])
local database = tonumber(ARGV[2])
if not new or not database or new <= database or new > 9007199254740990 or (old and new <= tonumber(old)) then return {-1} end
local count = redis.call('SCARD', KEYS[1])
if tostring(count) ~= ARGV[4] then return {-1} end
redis.call('HSET', KEYS[2], 'epoch', ARGV[3], 'version', '1', 'count', tostring(count))
local now = redis.call('TIME')
redis.call('ZADD', KEYS[3], now[1] * 1000 + math.floor(now[2] / 1000), ARGV[5])
return {1, count}
