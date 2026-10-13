local relationType = redis.call('TYPE', KEYS[1]).ok
if (relationType ~= 'none' and relationType ~= 'set') or redis.call('TYPE', KEYS[2]).ok ~= 'hash' then return {-1} end
local epoch = redis.call('HGET', KEYS[2], 'epoch')
local version = redis.call('HGET', KEYS[2], 'version')
if not tonumber(epoch) or not tonumber(version)
    or tonumber(redis.call('HGET', KEYS[2], 'count')) ~= redis.call('SCARD', KEYS[1]) then return {-1} end
return {epoch, version, tostring(redis.call('SCARD', KEYS[1]))}
