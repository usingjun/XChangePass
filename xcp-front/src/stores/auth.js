import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        isLoggedIn: false,
        isReady: false
    }),
    actions: {
        login() {
            this.isLoggedIn = true
            localStorage.setItem('isLoggedIn', 'true')
        },
        logout() {
            this.isLoggedIn = false
            localStorage.setItem('isLoggedIn', 'false')
        },
        async checkAuth() {
            try {
                const res = await fetch('http://localhost:8080/api/v1/user', {
                    credentials: 'include'
                })
                this.isLoggedIn = res.ok
            } catch {
                this.isLoggedIn = false
            } finally {
                this.isReady = true
            }
        }
    }
})
