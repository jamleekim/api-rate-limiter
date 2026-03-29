-- sliding_window.lua
-- KEYS[1]: window key
-- ARGV[1]: capacity, ARGV[2]: windowSizeMs, ARGV[3]: current time (ms)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local windowSize = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local windowStart = now - windowSize

-- remove entries outside the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

local currentCount = redis.call('ZCARD', key)

if currentCount < capacity then
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, windowSize + 1000)
    local remaining = capacity - currentCount - 1
    return {1, remaining, 0}
else
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 1
    if #oldest >= 2 then
        retryAfter = math.max(tonumber(oldest[2]) + windowSize - now, 1)
    end
    return {0, 0, retryAfter}
end
