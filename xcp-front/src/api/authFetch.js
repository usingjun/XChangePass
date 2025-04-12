import { useAuthStore } from '@/stores/auth'

export async function authFetch(url, options = {}) {
    const baseOptions = {
        ...options,
        credentials: 'include'
    }

    let res = await fetch(url, baseOptions)

    if (res.status !== 401) return res

    if (!isRefreshing) {
        isRefreshing = true
        const refreshRes = await fetch('http://localhost:8080/token-refresh', {
            method: 'POST',
            credentials: 'include'
        })

        isRefreshing = false

        if (refreshRes.ok) {
            await processQueue(true, url, baseOptions)
            return await fetch(url, baseOptions)
        } else {
            const auth = useAuthStore()
            auth.logout() // ✅ 상태 초기화
            await processQueue(false)
            alert('세션이 만료되었습니다. 다시 로그인해주세요.')
            window.location.href = '/user-login'
            return res
        }
    }

    return new Promise((resolve, reject) => {
        queue.push(async (success, retryUrl, retryOptions) => {
            if (success) {
                const retryRes = await fetch(retryUrl, retryOptions)
                resolve(retryRes)
            } else {
                reject(res)
            }
        })
    })
}
