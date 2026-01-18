-- KEYS[1] = 거래 기록 key (e.g., fraud:user:1234)
-- ARGV[1] = 현재 금액
-- ARGV[2] = 현재 timestamp (epoch)
-- ARGV[3] = 10분 누적 한도 (500000)
-- ARGV[4] = 5분 거래 횟수 한도 (5)
-- ARGV[5] = 새 거래를 history에 추가할지 여부 ("true" or "false")
-- ARGV[6] = isNightFlag ("1"이면 심야시간)

local key = KEYS[1]
local currentAmount = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local totalLimit = tonumber(ARGV[3])
local freqLimit = tonumber(ARGV[4])
local addFlag = ARGV[5]
local isNight = tonumber(ARGV[6])

local startTime = now - 600
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

        if timestamp >= now - 300 then
            count5min = count5min + 1
        end

        table.insert(recentAmounts, 1, amount)
        if #recentAmounts > 3 then
            table.remove(recentAmounts)
        end
    end
end

-- 룰1: 총합
if sum + currentAmount > totalLimit then
    return "1"
end

-- 룰2: 최근 5분간 5건 이상
if count5min >= freqLimit then
    return "2"
end

-- 룰3: 최근 거래 3건 동일
if #recentAmounts == 3 and
   recentAmounts[1] == currentAmount and
   recentAmounts[2] == currentAmount and
   recentAmounts[3] == currentAmount then
    return "3"
end

-- 룰4: 심야 시간 거래
if isNight == 1 then
    return "4"
end

-- 기록 추가
if addFlag == "true" then
    local value = tostring(currentAmount) .. "|" .. tostring(now) .. "|" .. tostring(math.random())
    redis.call("ZADD", key, now, value)
    redis.call("EXPIRE", key, 7200)
end

return "0"
