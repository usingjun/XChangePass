import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import HomeView from '@/views/HomeView.vue'
import WalletView from '@/views/WalletView.vue'

const routes = [
    { path: '/', name: 'Home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/user-login', name: 'Login', component: LoginView },
    { path: '/register', name: 'Register', component: RegisterView },
    { path: '/wallet', name: 'Wallet', component: WalletView, meta: { requiresAuth: true } },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to, from, next) => {
    const auth = useAuthStore()

    // ✅ checkAuth가 아직 안 끝났으면 기다려야 함
    if (!auth.isReady) {
        await auth.checkAuth()
    }

    // ✅ 라우트 보호
    if (to.meta.requiresAuth && !auth.isLoggedIn) {
        return next('/user-login')
    }

    return next()
})

export default router
