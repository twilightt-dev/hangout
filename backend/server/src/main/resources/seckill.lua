-- ARGV[1]：用户 ID
-- ARGV[2]：优惠券 ID
-- ARGV[3]：订单 ID
local userId = ARGV[1]
local voucherId = ARGV[2]
local orderId = ARGV[3]

--KEYS[1] 库存对应的key
--KEYS[2] 已购买的用户列表对应的key
--KEYS[3] Stream Key
local stockKey = KEYS[1]
local userKey = KEYS[2]
local streamKey = KEYS[3]

-- 库存未初始化或不足
local stock = tonumber(redis.call('get', stockKey))
if not stock or stock <= 0 then
	return 1
end

-- 重复购买
if redis.call('sismember', userKey, userId) == 1 then
	return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', userKey, userId)

--把消息加入Stream队列
redis.call('xadd' , streamKey , '*' , 'userId' , userId , 'voucherId' , voucherId , 'orderId' ,
orderId)

return 0