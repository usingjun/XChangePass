import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

export const transferDuration = new Trend('transfer_duration', true);
export const statisticsDuration = new Trend('statistics_duration', true);
export const transferErrorRate = new Rate('transfer_error_rate');
export const statisticsErrorRate = new Rate('statistics_error_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STATS_MODE = __ENV.STATS_MODE || 'GROUP_BY';
const FROM_MONTH = __ENV.FROM_MONTH || '2026-01';
const TO_MONTH = __ENV.TO_MONTH || '2026-12';
const STATS_USER_IDS = parseList(__ENV.STATS_USER_IDS || __ENV.USER_IDS || __ENV.TEST_USER_ID || '');
const STATS_TOKEN = __ENV.STATS_ACCESS_TOKEN_COOKIE || __ENV.STATS_AUTH_TOKEN || __ENV.ACCESS_TOKEN_COOKIE || '';

const SENDER_USER_IDS = parseList(__ENV.SENDER_USER_IDS || __ENV.SENDER_USER_ID || '');
const RECEIVER_USER_IDS = parseList(__ENV.RECEIVER_USER_IDS || __ENV.RECEIVER_USER_ID || '');
const RECEIVER_NAMES = parseList(__ENV.RECEIVER_NAMES || __ENV.RECEIVER_NAME || '');
const RECEIVER_PHONE_NUMBERS = parseList(__ENV.RECEIVER_PHONE_NUMBERS || __ENV.RECEIVER_PHONE_NUMBER || '');
const TRANSFER_TOKENS = parseList(__ENV.TRANSFER_AUTH_TOKENS || __ENV.AUTH_TOKENS || '');
const FROM_CURRENCY = __ENV.FROM_CURRENCY || __ENV.CURRENCY || 'KRW';
const TO_CURRENCY = __ENV.TO_CURRENCY || __ENV.CURRENCY || 'KRW';
const TRANSFER_AMOUNT = __ENV.TRANSFER_AMOUNT || '1.00';
const REQUEST_SLEEP_SECONDS = Number(__ENV.K6_SLEEP || '1');
const DEBUG_STATUS = (__ENV.DEBUG_STATUS || 'false') === 'true';
const VU_COUNT = Number(__ENV.K6_VUS || '10');

export const options = {
    vus: Number(__ENV.K6_VUS || '10'),
    duration: __ENV.K6_DURATION || '30s',
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        transfer_error_rate: ['rate<0.05'],
        statistics_error_rate: ['rate<0.05'],
    },
};

export default function () {
    runTransfer();
    runStatistics();
    sleep(REQUEST_SLEEP_SECONDS);
}

function runTransfer() {
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
            mixed_mode: STATS_MODE,
        },
    });

    transferDuration.add(res.timings.duration);
    const ok = check(res, {
        'transfer status is 200': (response) => response.status === 200,
        'transfer completed': (response) => response.status === 200 && response.body.includes('COMPLETED'),
    });

    if (!ok && DEBUG_STATUS && __VU === 1 && __ITER === 0) {
        console.log(`transfer debug status=${res.status} body=${String(res.body).slice(0, 300)}`);
    }

    transferErrorRate.add(!ok);
}

function runStatistics() {
    const userId = valueAt(STATS_USER_IDS, (__ITER + __VU - 1), 'statsUserId');
    const url = `${BASE_URL}/api/v1/transactions/statistics/monthly`
        + `?userId=${encodeURIComponent(userId)}`
        + `&fromMonth=${encodeURIComponent(FROM_MONTH)}`
        + `&toMonth=${encodeURIComponent(TO_MONTH)}`
        + `&mode=${encodeURIComponent(STATS_MODE)}`;

    const res = http.get(url, {
        headers: {
            Accept: 'application/json',
        },
        cookies: {
            accessToken: STATS_TOKEN,
        },
        tags: {
            endpoint: 'transaction_statistics_monthly',
            mixed_mode: STATS_MODE,
        },
    });

    statisticsDuration.add(res.timings.duration);
    const ok = check(res, {
        'statistics status is 200': (response) => response.status === 200,
    });

    if (!ok && DEBUG_STATUS && __VU === 1 && __ITER === 0) {
        console.log(`statistics debug status=${res.status} body=${String(res.body).slice(0, 300)}`);
    }

    statisticsErrorRate.add(!ok);
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

    return ((__ITER * VU_COUNT) + (__VU - 1)) % pairCount;
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
