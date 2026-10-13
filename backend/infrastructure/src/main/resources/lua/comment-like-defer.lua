if redis.call('HGET', KEYS[2], 'epoch') == ARGV[1] and redis.call('HGET', KEYS[2], 'version') == ARGV[2]
    and redis.call('ZSCORE', KEYS[3], ARGV[3]) then
    local now = redis.call('TIME')
    redis.call('ZADD', KEYS[3], now[1] * 1000 + math.floor(now[2] / 1000) + 1000, ARGV[3])
end
return 0
