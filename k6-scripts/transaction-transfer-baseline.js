import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const transferErrorRate = new Rate('transfer_error_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SENDER_USER_IDS = parseList(__ENV.SENDER_USER_IDS || __ENV.SENDER_USER_ID || '');
const RECEIVER_USER_IDS = parseList(__ENV.RECEIVER_USER_IDS || __ENV.RECEIVER_USER_ID || '');
const RECEIVER_NAMES = parseList(__ENV.RECEIVER_NAMES || __ENV.RECEIVER_NAME || '');
const RECEIVER_PHONE_NUMBERS = parseList(__ENV.RECEIVER_PHONE_NUMBERS || __ENV.RECEIVER_PHONE_NUMBER || '');
const AUTH_TOKENS = parseList(__ENV.AUTH_TOKENS || __ENV.AUTH_TOKEN || __ENV.ACCESS_TOKEN_COOKIE || '');
const FROM_CURRENCY = __ENV.FROM_CURRENCY || __ENV.CURRENCY || 'KRW';
const TO_CURRENCY = __ENV.TO_CURRENCY || __ENV.CURRENCY || 'KRW';
const TRANSFER_AMOUNT = __ENV.TRANSFER_AMOUNT || '1.00';
const REQUEST_SLEEP_SECONDS = Number(__ENV.K6_SLEEP || '0.1');
const DEBUG_STATUS = (__ENV.DEBUG_STATUS || 'false') === 'true';
const VU_COUNT = Number(__ENV.K6_VUS || '10');

export const options = {
    vus: Number(__ENV.K6_VUS || '10'),
    duration: __ENV.K6_DURATION || '30s',
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        transfer_error_rate: ['rate<0.05'],
    },
};

export default function () {
    const pairIndex = selectPairIndex();
    const receiverName = valueAt(RECEIVER_NAMES, pairIndex, 'receiverName');
    const receiverPhoneNumber = valueAt(RECEIVER_PHONE_NUMBERS, pairIndex, 'receiverPhoneNumber');
    const token = valueAt(AUTH_TOKENS, pairIndex, 'authToken');
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
            scenario: 'transfer_baseline',
        },
    });

    const ok = check(res, {
        'transfer status is 200': (response) => response.status === 200,
        'transfer completed': (response) => response.status === 200 && response.body.includes('COMPLETED'),
    });

    if (!ok && DEBUG_STATUS && __VU === 1 && __ITER === 0) {
        console.log(`transfer debug status=${res.status} body=${String(res.body).slice(0, 300)}`);
    }

    transferErrorRate.add(!ok);
    sleep(REQUEST_SLEEP_SECONDS);
}

function selectPairIndex() {
    const pairCount = Math.min(
        positiveLength(SENDER_USER_IDS),
        positiveLength(RECEIVER_USER_IDS),
        positiveLength(RECEIVER_NAMES),
        positiveLength(RECEIVER_PHONE_NUMBERS),
        positiveLength(AUTH_TOKENS)
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
