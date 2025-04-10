export async function authFetch(url, options = {}) {
    const baseOptions = {
        ...options,
        credentials: 'include',
    }

    let res = await fetch(url, baseOptions)

    if (res.status === 401) {
        const refreshRes = await fetch('http://localhost:8080/token-refresh', {
            method: 'POST',
            credentials: 'include',
        })

        if (refreshRes.ok) {
            // 재시도
            res = await fetch(url, baseOptions)
        } else {
            alert('세션 만료. 다시 로그인해주세요.')
            window.location.href = '/user-login'
        }
    }

    return res
}
