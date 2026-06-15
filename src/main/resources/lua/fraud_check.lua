-- KEYS[1] = 거래 기록 key (e.g., fraud:user:1234)
-- KEYS[2] = 요청별 탐지 결과 key
-- ARGV[1] = 현재 금액
-- ARGV[2] = 현재 timestamp (epoch)
-- ARGV[3] = 10분 누적 한도 (500000)
-- ARGV[4] = 5분 거래 횟수 한도 (5)
-- ARGV[5] = isNightFlag ("1"이면 심야시간)
-- ARGV[6] = 거래 기록 식별자
-- ARGV[7] = 누적 금액 평가 구간(초)
-- ARGV[8] = 거래 빈도 평가 구간(초)
-- ARGV[9] = 거래 이력 TTL(초)

local key = KEYS[1]
local resultKey = KEYS[2]
local currentAmount = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local totalLimit = tonumber(ARGV[3])
local freqLimit = tonumber(ARGV[4])
local isNight = tonumber(ARGV[5])
local recordId = ARGV[6]
local accumulationWindow = tonumber(ARGV[7])
local frequencyWindow = tonumber(ARGV[8])
local historyTtl = tonumber(ARGV[9])

local cachedResult = redis.call("HGET", resultKey, recordId)
if cachedResult then
    return cachedResult
end

local startTime = now - accumulationWindow
redis.call("ZREMRANGEBYSCORE", key, "-inf", startTime - 1)
local history = redis.call("ZRANGEBYSCORE", key, startTime, now)

local sum = 0
local recentAmounts = {}
local count5min = 0

-- 최근 10분 이내 기록 조회
for _, record in ipairs(history) do
    local amountStr, timestampStr = record:match("([^|]+)|([^|]+)")
    if amountStr and timestampStr then
        local amount = tonumber(amountStr)
        local timestamp = tonumber(timestampStr)

        sum = sum + amount

        if timestamp >= now - frequencyWindow then
            count5min = count5min + 1
        end

        table.insert(recentAmounts, 1, amount)
        if #recentAmounts > 3 then
            table.remove(recentAmounts)
        end
    end
end

local reasons = {}

if sum + currentAmount > totalLimit then
    table.insert(reasons, "1")
end
if count5min >= freqLimit then
    table.insert(reasons, "2")
end
if #recentAmounts == 3 and
       recentAmounts[1] == currentAmount and
       recentAmounts[2] == currentAmount and
       recentAmounts[3] == currentAmount then
    table.insert(reasons, "3")
end
if isNight == 1 then
    table.insert(reasons, "4")
end
local result = #reasons == 0 and "0" or table.concat(reasons, ",")

-- 정상/의심 여부와 관계없이 모든 거래 시도를 기록한다.
local value = tostring(currentAmount) .. "|" .. tostring(now) .. "|" .. recordId
redis.call("ZADD", key, now, value)
redis.call("EXPIRE", key, historyTtl)
redis.call("HSET", resultKey, recordId, result)
redis.call("EXPIRE", resultKey, historyTtl)

return result
