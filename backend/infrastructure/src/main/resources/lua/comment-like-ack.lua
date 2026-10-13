if redis.call('HGET', KEYS[2], 'epoch') == ARGV[1] and redis.call('HGET', KEYS[2], 'version') == ARGV[2] then
    return redis.call('ZREM', KEYS[3], ARGV[3])
end
return 0
