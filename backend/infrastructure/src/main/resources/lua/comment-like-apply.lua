local function validType(key, expected)
    local t = redis.call('TYPE', key).ok
    return t == 'none' or t == expected
end
if not validType(KEYS[1], 'set') or not validType(KEYS[2], 'hash') or not validType(KEYS[3], 'zset') then return {-1} end
if redis.call('HGET', KEYS[2], 'epoch') ~= ARGV[1] then return {-1} end
local rawVersion = redis.call('HGET', KEYS[2], 'version')
local version = tonumber(rawVersion)
local count = redis.call('SCARD', KEYS[1])
if not rawVersion or not (rawVersion == '0' or string.match(rawVersion, '^[1-9]%d*$'))
    or not version or version < 0 or version >= 9007199254740990 or count > 4294967295
    or tonumber(redis.call('HGET', KEYS[2], 'count')) ~= count then return {-1} end
local actual = redis.call('SISMEMBER', KEYS[1], ARGV[2])
if actual ~= tonumber(ARGV[3]) then return {0, actual, count} end
if ARGV[4] == '1' and count >= 4294967295 then return {-1} end
if ARGV[4] == '1' then redis.call('SADD', KEYS[1], ARGV[2]) else redis.call('SREM', KEYS[1], ARGV[2]) end
redis.call('HINCRBY', KEYS[2], 'version', 1)
redis.call('HSET', KEYS[2], 'count', redis.call('SCARD', KEYS[1]))
local now = redis.call('TIME')
redis.call('ZADD', KEYS[3], now[1] * 1000 + math.floor(now[2] / 1000), ARGV[5])
return {1, tonumber(ARGV[4]), redis.call('SCARD', KEYS[1])}
