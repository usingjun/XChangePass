import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

export const transferDuration = new Trend('transfer_duration', true);
export const statisticsDuration = new Trend('statistics_duration', true);
export const transferBlockedDuration = new Trend('transfer_blocked_duration', true);
export const transferWaitingDuration = new Trend('transfer_waiting_duration', true);
export const transferReceivingDuration = new Trend('transfer_receiving_duration', true);
export const statisticsBlockedDuration = new Trend('statistics_blocked_duration', true);
export const statisticsWaitingDuration = new Trend('statistics_waiting_duration', true);
export const statisticsReceivingDuration = new Trend('statistics_receiving_duration', true);
export const transferErrorRate = new Rate('transfer_error_rate');
export const statisticsErrorRate = new Rate('statistics_error_rate');
export const transferDurationPreBurst = new Trend('transfer_duration_pre_burst', true);
export const transferDurationBurst = new Trend('transfer_duration_burst', true);
export const transferDurationPostBurst = new Trend('transfer_duration_post_burst', true);
export const statisticsDurationPreBurst = new Trend('statistics_duration_pre_burst', true);
export const statisticsDurationBurst = new Trend('statistics_duration_burst', true);
export const statisticsDurationPostBurst = new Trend('statistics_duration_post_burst', true);
export const statisticsWaitingPreBurst = new Trend('statistics_waiting_pre_burst', true);
export const statisticsWaitingBurst = new Trend('statistics_waiting_burst', true);
export const statisticsWaitingPostBurst = new Trend('statistics_waiting_post_burst', true);
export const statisticsBlockedPreBurst = new Trend('statistics_blocked_pre_burst', true);
export const statisticsBlockedBurst = new Trend('statistics_blocked_burst', true);
export const statisticsBlockedPostBurst = new Trend('statistics_blocked_post_burst', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || __ENV.STATS_MODE || 'MATERIALIZED_VIEW';
const SCENARIO_LABEL = __ENV.SCENARIO_LABEL || 'transaction-statistics-replica-car';
const TRANSFER_ENV = parseEnvFile(__ENV.TRANSFER_AUTH_ENV_FILE || '');
const STATS_ENV = parseEnvFile(__ENV.STATS_AUTH_ENV_FILE || '');
const FROM_MONTH = __ENV.FROM_MONTH || STATS_ENV.FROM_MONTH || '2026-01';
const TO_MONTH = __ENV.TO_MONTH || STATS_ENV.TO_MONTH || '2026-12';
const STATS_USER_IDS = parseList(__ENV.STATS_USER_IDS || __ENV.USER_IDS || __ENV.TEST_USER_ID
    || STATS_ENV.USER_IDS || STATS_ENV.TEST_USER_ID || '');
const STATS_TOKEN = __ENV.ACCESS_TOKEN
    || __ENV.STATS_ACCESS_TOKEN_COOKIE
    || __ENV.STATS_AUTH_TOKEN
    || __ENV.ACCESS_TOKEN_COOKIE
    || STATS_ENV.ACCESS_TOKEN_COOKIE
    || STATS_ENV.AUTH_TOKEN
    || STATS_ENV.ACCESS_TOKEN
    || STATS_ENV.JWT_TOKEN
    || '';

const SENDER_USER_IDS = parseList(__ENV.SENDER_USER_IDS || __ENV.SENDER_USER_ID
    || TRANSFER_ENV.SENDER_USER_IDS || TRANSFER_ENV.SENDER_USER_ID || '');
const RECEIVER_USER_IDS = parseList(__ENV.RECEIVER_USER_IDS || __ENV.RECEIVER_USER_ID
    || TRANSFER_ENV.RECEIVER_USER_IDS || TRANSFER_ENV.RECEIVER_USER_ID || '');
const RECEIVER_NAMES = parseList(__ENV.RECEIVER_NAMES || __ENV.RECEIVER_NAME
    || TRANSFER_ENV.RECEIVER_NAMES || TRANSFER_ENV.RECEIVER_NAME || '');
const RECEIVER_PHONE_NUMBERS = parseList(__ENV.RECEIVER_PHONE_NUMBERS || __ENV.RECEIVER_PHONE_NUMBER
    || TRANSFER_ENV.RECEIVER_PHONE_NUMBERS || TRANSFER_ENV.RECEIVER_PHONE_NUMBER || '');
const TRANSFER_TOKENS = parseList(__ENV.TRANSFER_AUTH_TOKENS || __ENV.AUTH_TOKENS
    || TRANSFER_ENV.AUTH_TOKENS || TRANSFER_ENV.AUTH_TOKEN || '');
const FROM_CURRENCY = __ENV.FROM_CURRENCY || __ENV.CURRENCY || TRANSFER_ENV.FROM_CURRENCY
    || TRANSFER_ENV.CURRENCY || 'KRW';
const TO_CURRENCY = __ENV.TO_CURRENCY || __ENV.CURRENCY || TRANSFER_ENV.TO_CURRENCY
    || TRANSFER_ENV.CURRENCY || 'KRW';
const TRANSFER_AMOUNT = __ENV.TRANSFER_AMOUNT || TRANSFER_ENV.TRANSFER_AMOUNT || '1.00';
const DEBUG_STATUS = (__ENV.DEBUG_STATUS || 'false') === 'true';
const STARTED_AT = Date.now();
const STATISTICS_BURST_ENABLED = (__ENV.STATISTICS_BURST_ENABLED || 'false') === 'true';
const STATISTICS_PRE_DURATION = __ENV.STATISTICS_PRE_DURATION || '4m';
const STATISTICS_BURST_DURATION = __ENV.STATISTICS_BURST_DURATION || '2m';
const STATISTICS_POST_DURATION = __ENV.STATISTICS_POST_DURATION || '4m';
const STATISTICS_PRE_RATE = Number(__ENV.STATISTICS_PRE_RATE || '50');
const STATISTICS_BURST_RATE = Number(__ENV.STATISTICS_BURST_RATE || '300');
const STATISTICS_POST_RATE = Number(__ENV.STATISTICS_POST_RATE || '50');
const STATISTICS_PRE_ALLOCATED_VUS = Number(__ENV.STATISTICS_PRE_VUS || __ENV.STATISTICS_VUS || '20');
const STATISTICS_BURST_ALLOCATED_VUS = Number(__ENV.STATISTICS_BURST_VUS || __ENV.STATISTICS_VUS || '60');
const STATISTICS_POST_ALLOCATED_VUS = Number(__ENV.STATISTICS_POST_VUS || __ENV.STATISTICS_VUS || '20');
const STATISTICS_PRE_MAX_VUS = Number(__ENV.STATISTICS_PRE_MAX_VUS || __ENV.STATISTICS_MAX_VUS || '50');
const STATISTICS_BURST_MAX_VUS = Number(__ENV.STATISTICS_BURST_MAX_VUS || __ENV.STATISTICS_MAX_VUS || '120');
const STATISTICS_POST_MAX_VUS = Number(__ENV.STATISTICS_POST_MAX_VUS || __ENV.STATISTICS_MAX_VUS || '50');

export const options = {
    scenarios: buildScenarios(),
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        transfer_error_rate: ['rate<0.05'],
        statistics_error_rate: ['rate<0.05'],
    },
};

export function transferScenario() {
    const pairIndex = selectPairIndex();
    const receiverName = valueAt(RECEIVER_NAMES, pairIndex, 'receiverName');
    const receiverPhoneNumber = valueAt(RECEIVER_PHONE_NUMBERS, pairIndex, 'receiverPhoneNumber');
    const token = valueAt(TRANSFER_TOKENS, pairIndex, 'transferToken');
    const idempotencyKey = `${randomHex(8)}-${randomHex(4)}-4${randomHex(3)}-a${randomHex(3)}-${randomHex(12)}`;

    const payload = JSON.stringify({
        receiverName,
        receiverPhoneNumber,
        transferAmount: TRANSFER_AMOUNT,
        fromCurrency: FROM_CURRENCY,
        toCurrency: TO_CURRENCY,
        transferDatetime: null,
        transferType: 'GENERAL',
    });

    const res = http.put(`${BASE_URL}/api/v1/wallet/transfer`, payload, {
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
        cookies: {
            accessToken: token,
        },
        tags: {
            endpoint: 'wallet_transfer',
            experiment: 'transaction_statistics_replica_car',
        },
    });

    transferDuration.add(res.timings.duration);
    transferBlockedDuration.add(res.timings.blocked);
    transferWaitingDuration.add(res.timings.waiting);
    transferReceivingDuration.add(res.timings.receiving);
    recordTransferPhase(res);
    const ok = check(res, {
        'transfer status is 200': (response) => response.status === 200,
        'transfer completed': (response) => response.status === 200 && response.body.includes('COMPLETED'),
    });

    if (!ok && DEBUG_STATUS) {
        console.log(`transfer debug status=${res.status} body=${String(res.body).slice(0, 300)}`);
    }

    transferErrorRate.add(!ok);
}

export function statisticsScenario() {
    requestStatistics('all');
}

export function statisticsPreBurstScenario() {
    requestStatistics('pre-burst');
}

export function statisticsBurstScenario() {
    requestStatistics('burst');
}

export function statisticsPostBurstScenario() {
    requestStatistics('post-burst');
}

function requestStatistics(phase) {
    const userId = valueAt(STATS_USER_IDS, __ITER + __VU - 1, 'statsUserId');
    const url = `${BASE_URL}/api/v1/transactions/statistics/monthly`
        + `?userId=${encodeURIComponent(userId)}`
        + `&fromMonth=${encodeURIComponent(FROM_MONTH)}`
        + `&toMonth=${encodeURIComponent(TO_MONTH)}`
        + `&mode=${encodeURIComponent(MODE)}`;

    const res = http.get(url, {
        headers: {
            Accept: 'application/json',
        },
        cookies: {
            accessToken: STATS_TOKEN,
        },
        tags: {
            endpoint: 'transaction_statistics_monthly',
            mode: MODE,
            experiment: 'transaction_statistics_replica_car',
        },
    });

    statisticsDuration.add(res.timings.duration);
    statisticsBlockedDuration.add(res.timings.blocked);
    statisticsWaitingDuration.add(res.timings.waiting);
    statisticsReceivingDuration.add(res.timings.receiving);
    recordStatisticsPhase(res, phase);
    const ok = check(res, {
        'statistics status is 200': (response) => response.status === 200,
    });

    if (!ok && DEBUG_STATUS) {
        console.log(`statistics debug status=${res.status} body=${String(res.body).slice(0, 300)}`);
    }

    statisticsErrorRate.add(!ok);
}

function buildScenarios() {
    const transfers = {
        executor: 'constant-arrival-rate',
        rate: Number(__ENV.TRANSFER_RATE || '10'),
        timeUnit: '1s',
        duration: __ENV.DURATION || '30s',
        preAllocatedVUs: Number(__ENV.TRANSFER_VUS || '20'),
        maxVUs: Number(__ENV.TRANSFER_MAX_VUS || '50'),
        exec: 'transferScenario',
    };

    if (!STATISTICS_BURST_ENABLED) {
        return {
            transfers,
            statistics: {
                executor: 'constant-arrival-rate',
                rate: Number(__ENV.STATISTICS_RATE || '10'),
                timeUnit: '1s',
                duration: __ENV.DURATION || '30s',
                preAllocatedVUs: Number(__ENV.STATISTICS_VUS || '20'),
                maxVUs: Number(__ENV.STATISTICS_MAX_VUS || '50'),
                exec: 'statisticsScenario',
            },
        };
    }

    return {
        transfers,
        statistics_pre_burst: {
            executor: 'constant-arrival-rate',
            rate: STATISTICS_PRE_RATE,
            timeUnit: '1s',
            duration: STATISTICS_PRE_DURATION,
            preAllocatedVUs: STATISTICS_PRE_ALLOCATED_VUS,
            maxVUs: STATISTICS_PRE_MAX_VUS,
            exec: 'statisticsPreBurstScenario',
        },
        statistics_burst: {
            executor: 'constant-arrival-rate',
            rate: STATISTICS_BURST_RATE,
            timeUnit: '1s',
            startTime: STATISTICS_PRE_DURATION,
            duration: STATISTICS_BURST_DURATION,
            preAllocatedVUs: STATISTICS_BURST_ALLOCATED_VUS,
            maxVUs: STATISTICS_BURST_MAX_VUS,
            exec: 'statisticsBurstScenario',
        },
        statistics_post_burst: {
            executor: 'constant-arrival-rate',
            rate: STATISTICS_POST_RATE,
            timeUnit: '1s',
            startTime: addDurationStrings(STATISTICS_PRE_DURATION, STATISTICS_BURST_DURATION),
            duration: STATISTICS_POST_DURATION,
            preAllocatedVUs: STATISTICS_POST_ALLOCATED_VUS,
            maxVUs: STATISTICS_POST_MAX_VUS,
            exec: 'statisticsPostBurstScenario',
        },
    };
}

function recordTransferPhase(response) {
    const phase = currentPhase();
    if (phase === 'all') {
        return;
    }
    if (phase === 'pre-burst') {
        transferDurationPreBurst.add(response.timings.duration);
    } else if (phase === 'burst') {
        transferDurationBurst.add(response.timings.duration);
    } else {
        transferDurationPostBurst.add(response.timings.duration);
    }
}

function recordStatisticsPhase(response, phase) {
    if (phase === 'all') {
        return;
    }
    if (phase === 'pre-burst') {
        statisticsDurationPreBurst.add(response.timings.duration);
        statisticsWaitingPreBurst.add(response.timings.waiting);
        statisticsBlockedPreBurst.add(response.timings.blocked);
    } else if (phase === 'burst') {
        statisticsDurationBurst.add(response.timings.duration);
        statisticsWaitingBurst.add(response.timings.waiting);
        statisticsBlockedBurst.add(response.timings.blocked);
    } else {
        statisticsDurationPostBurst.add(response.timings.duration);
        statisticsWaitingPostBurst.add(response.timings.waiting);
        statisticsBlockedPostBurst.add(response.timings.blocked);
    }
}

function currentPhase() {
    if (!STATISTICS_BURST_ENABLED) {
        return 'all';
    }
    const elapsedSeconds = (Date.now() - STARTED_AT) / 1000;
    const preSeconds = durationSeconds(STATISTICS_PRE_DURATION);
    const burstSeconds = durationSeconds(STATISTICS_BURST_DURATION);
    if (elapsedSeconds < preSeconds) {
        return 'pre-burst';
    }
    if (elapsedSeconds < preSeconds + burstSeconds) {
        return 'burst';
    }
    return 'post-burst';
}

function addDurationStrings(left, right) {
    return `${durationSeconds(left) + durationSeconds(right)}s`;
}

function durationSeconds(value) {
    const match = String(value).trim().match(/^([0-9]+(?:\.[0-9]+)?)(ms|s|m|h)$/);
    if (!match) {
        throw new Error(`Unsupported duration: ${value}`);
    }
    const amount = Number(match[1]);
    const unit = match[2];
    if (unit === 'ms') {
        return amount / 1000;
    }
    if (unit === 's') {
        return amount;
    }
    if (unit === 'm') {
        return amount * 60;
    }
    return amount * 3600;
}

function selectPairIndex() {
    const pairCount = Math.min(
        positiveLength(SENDER_USER_IDS),
        positiveLength(RECEIVER_USER_IDS),
        positiveLength(RECEIVER_NAMES),
        positiveLength(RECEIVER_PHONE_NUMBERS),
        positiveLength(TRANSFER_TOKENS)
    );

    if (pairCount <= 0) {
        throw new Error('transfer pair env is required.');
    }

    const stride = Number(__ENV.PAIR_SELECTION_STRIDE || __ENV.TRANSFER_MAX_VUS || '50');
    const offset = hashString(SCENARIO_LABEL) % pairCount;
    return positiveModulo(offset + (__ITER * stride) + (__VU - 1), pairCount);
}

function valueAt(values, index, name) {
    if (!values || values.length === 0) {
        throw new Error(`${name} env is required.`);
    }
    return values[index % values.length];
}

function parseList(value) {
    return value
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function parseEnvFile(path) {
    if (!path) {
        return {};
    }

    const result = {};
    const content = open(path);
    content
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0 && !line.startsWith('#'))
        .forEach((line) => {
            const separator = line.indexOf('=');
            if (separator > 0) {
                const key = line.slice(0, separator).trim();
                const value = line.slice(separator + 1).trim();
                result[key] = value;
            }
        });
    return result;
}

function positiveLength(values) {
    return values && values.length > 0 ? values.length : 0;
}

function randomHex(length) {
    const alphabet = '0123456789abcdef';
    let result = '';
    for (let index = 0; index < length; index++) {
        result += alphabet[Math.floor(Math.random() * alphabet.length)];
    }
    return result;
}

function hashString(value) {
    let hash = 2166136261;
    for (let index = 0; index < value.length; index++) {
        hash ^= value.charCodeAt(index);
        hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
}

function positiveModulo(value, divisor) {
    return ((value % divisor) + divisor) % divisor;
}
