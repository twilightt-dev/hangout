-- 初始化只在持久化标记与数据库行锁允许时执行；已用数据缺失必须暂停。
local function validType(key, expected)
    local t = redis.call('TYPE', key).ok
    return t == 'none' or t == expected
end
if not validType(KEYS[1], 'set') or not validType(KEYS[2], 'hash') or not validType(KEYS[3], 'zset') then return {-1} end
local epoch = redis.call('HGET', KEYS[2], 'epoch')
if not epoch then
    if ARGV[3] ~= '1' or redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then return {-1} end
    redis.call('HSET', KEYS[2], 'epoch', ARGV[1], 'version', '0', 'count', '0')
elseif epoch ~= ARGV[1] then return {-1} end
local rawVersion = redis.call('HGET', KEYS[2], 'version')
local version = tonumber(rawVersion)
if not rawVersion or not (rawVersion == '0' or string.match(rawVersion, '^[1-9]%d*$'))
    or not version or version < 0 or version > 9007199254740990
    or tonumber(redis.call('HGET', KEYS[2], 'count')) ~= redis.call('SCARD', KEYS[1]) then return {-1} end
return {1, ARGV[2] == '' and 0 or redis.call('SISMEMBER', KEYS[1], ARGV[2])}
