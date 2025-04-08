import http from 'k6/http';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { check } from 'k6';

// 회원가입 함수
export function signupUser(BASE_URL) {
    let userEmail = `user_${randomString(10)}@test.com`;
    let userPwd = 'Test1234!';
    let userName = `홍길동`;
    let userPhoneNumber = `010-${randomIntBetween(1000, 9999)}-${randomIntBetween(1000, 9999)}`;
    let userSex = 'MALE';
    let walletPassword = '1234';

    let signupPayload = JSON.stringify({
        userEmail: userEmail,
        userPwd: userPwd,
        userName: userName,
        userPhoneNumber: userPhoneNumber,
        userSex: userSex,
        walletPassword: walletPassword
    });

    let params = { headers: { 'Content-Type': 'application/json' } };

    let signupRes = http.post(`${BASE_URL}/api/v1/user/signup`, signupPayload, params, {
        tags: { method: 'POST', endpoint: 'signup' },
    });

    check(signupRes, {
        'Signup success': (r) => r.status === 201,
    });

    if (signupRes.status !== 201) {
        return null;  // 회원가입 실패 시 null 반환
    }

    return { userEmail, userPwd };  // 로그인에 필요한 userEmail, userPwd 반환
}

// 로그인 함수
export function loginUser(BASE_URL, userEmail, userPwd) {
    let loginPayload = JSON.stringify({
        userEmail: userEmail,
        password: userPwd
    });

    let params = { headers: { 'Content-Type': 'application/json' } };

    let loginRes = http.post(`${BASE_URL}/login`, loginPayload, params, {
        tags: { method: 'POST', endpoint: 'login' },
    });

    check(loginRes, {
        'Login success': (r) => r.status === 200,
    });

    if (loginRes.status !== 200) {
        return null;  // 로그인 실패 시 null 반환
    }

    let authToken = JSON.parse(loginRes.body).accessToken;

    if (!authToken) {
        return null;  // JWT 토큰 없을 경우 null 반환
    }

    return authToken;  // 로그인 후 JWT 토큰 반환
}

// 내 정보 조회 함수
export function getUserInfo(BASE_URL, authToken) {
    let authParams = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${authToken}`
        }
    };

    let userRes = http.get(`${BASE_URL}/api/v1/user`, authParams, {
        tags: { method: 'GET', endpoint: 'user' },
    });

    check(userRes, {
        'Get User Info success': (r) => r.status === 200,
    });

    if (userRes.status !== 200) {
        return null;  // 정보 조회 실패 시 null 반환
    }

    return userRes.body;  // 응답 본문 반환
}

// 내 정보 수정 함수
export function updateUserInfo(BASE_URL, authToken) {
    let updatePayload = JSON.stringify({
        userNickname: `Test_${randomString(8)}`,
        userAge: 24,
        userSex: "FEMALE"
    });

    let authParams = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${authToken}`
        }
    };

    let updateRes = http.put(`${BASE_URL}/api/v1/user`, updatePayload, authParams, {
        tags: { method: 'PUT', endpoint: 'user' },
    });

    check(updateRes, {
        'Update User Info success': (r) => r.status === 204,  // No Content 응답 예상
    });

    if (updateRes.status !== 204) {
        return null;  // 수정 실패 시 null 반환
    }

    return updateRes.status;
}

// 회원 탈퇴 함수
export function deleteUser(BASE_URL, authToken) {
    let authParams = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${authToken}`
        }
    };

    let deleteRes = http.delete(`${BASE_URL}/api/v1/user`, null, authParams, {
        tags: { method: 'DELETE', endpoint: 'user' },
    });

    check(deleteRes, {
        'Delete User success': (r) => r.status === 204,  // No Content 응답 예상
    });

    if (deleteRes.status !== 204) {
        return null;  // 탈퇴 실패 시 null 반환
    }

    return deleteRes.status;
}
