-- token_bucket.lua
-- KEYS[1]: bucket key
-- ARGV[1]: capacity, ARGV[2]: refillRate, ARGV[3]: current time (ms)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    lastRefill = now
end

-- refill calculation
local elapsed = (now - lastRefill) / 1000.0
local tokensToAdd = elapsed * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)
lastRefill = now

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    redis.call('PEXPIRE', key, math.ceil(capacity / refillRate) * 1000 + 1000)
    return {1, math.floor(tokens), 0}
else
    local retryAfter = math.ceil(1000 / refillRate)
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    redis.call('PEXPIRE', key, math.ceil(capacity / refillRate) * 1000 + 1000)
    return {0, 0, retryAfter}
end
