import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const statisticsReadErrorRate = new Rate('statistics_read_error_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'GROUP_BY';
const FROM_MONTH = __ENV.FROM_MONTH || '2026-01';
const TO_MONTH = __ENV.TO_MONTH || '2026-12';
const USER_IDS = parseUserIds(__ENV.USER_IDS || __ENV.TEST_USER_ID || '1');
const REQUEST_SLEEP_SECONDS = Number(__ENV.K6_SLEEP || '0.1');
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || '';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || '';
const DEBUG_STATUS = (__ENV.DEBUG_STATUS || 'false') === 'true';

export const options = {
    vus: Number(__ENV.K6_VUS || '10'),
    duration: __ENV.K6_DURATION || '30s',
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        statistics_read_error_rate: ['rate<0.05'],
    },
};

export function setup() {
    const staticToken = __ENV.AUTH_TOKEN || __ENV.ACCESS_TOKEN || __ENV.JWT_TOKEN || '';
    const staticCookie = __ENV.ACCESS_TOKEN_COOKIE || '';

    if (staticToken || staticCookie) {
        return { token: staticToken, cookie: staticCookie };
    }

    if (!LOGIN_EMAIL || !LOGIN_PASSWORD) {
        return { token: '', cookie: '' };
    }

    const loginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({
            userEmail: LOGIN_EMAIL,
            password: LOGIN_PASSWORD,
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const bodyToken = parseAccessToken(loginRes.body);
    const cookieToken = extractAccessTokenCookie(loginRes);

    check(loginRes, {
        'login status is 200': (res) => res.status === 200,
        'login returns token': () => Boolean(bodyToken || cookieToken),
    });

    return { token: bodyToken, cookie: cookieToken };
}

export default function (auth) {
    const userId = USER_IDS[(__ITER + __VU - 1) % USER_IDS.length];
    const url = `${BASE_URL}/api/v1/transactions/statistics/monthly`
        + `?userId=${encodeURIComponent(userId)}`
        + `&fromMonth=${encodeURIComponent(FROM_MONTH)}`
        + `&toMonth=${encodeURIComponent(TO_MONTH)}`
        + `&mode=${encodeURIComponent(MODE)}`;

    const res = http.get(url, {
        headers: authHeaders(auth),
        cookies: authCookies(auth),
        tags: {
            endpoint: 'transaction_statistics_monthly',
            mode: MODE,
        },
    });

    const ok = check(res, {
        'statistics status is 200': (response) => response.status === 200,
    });

    if (!ok && DEBUG_STATUS && __VU === 1 && __ITER === 0) {
        console.log(`statistics debug status=${res.status} body=${String(res.body).slice(0, 200)}`);
    }

    statisticsReadErrorRate.add(!ok);
    sleep(REQUEST_SLEEP_SECONDS);
}

function authHeaders(auth) {
    const headers = {
        Accept: 'application/json',
    };

    if (auth && auth.token) {
        headers.Authorization = `Bearer ${auth.token}`;
    }

    return headers;
}

function authCookies(auth) {
    if (!auth || (!auth.cookie && !auth.token)) {
        return {};
    }

    return {
        accessToken: auth.cookie || auth.token,
    };
}

function parseUserIds(value) {
    return value
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function parseAccessToken(body) {
    if (!body) {
        return '';
    }

    try {
        const parsed = JSON.parse(body);
        return parsed.accessToken || '';
    } catch (error) {
        return '';
    }
}

function extractAccessTokenCookie(response) {
    const cookies = response.cookies && response.cookies.accessToken;
    return cookies && cookies.length > 0 ? cookies[0].value : '';
}
