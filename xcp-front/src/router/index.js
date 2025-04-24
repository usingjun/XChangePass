import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import HomeView from '@/views/HomeView.vue'
import ExchangeView from '@/views/ExchangeView.vue'  // 환율 조회 페이지 import 추가
import ExchangeTransferView from '@/views/ExchangeTransferView.vue'

const routes = [
    { path: '/', name: 'Home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/exchange', name: 'Exchange', component: ExchangeView, meta: { requiresAuth: true } },  // 추가
    { path: '/exchange-transfer', name: 'ExchangeTransfer', component: ExchangeTransferView, meta: { requiresAuth: true } },
    { path: '/user-login', name: 'Login', component: LoginView },
    { path: '/register', name: 'Register', component: RegisterView },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 로그인 검증 로직
router.beforeEach((to, from, next) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isLoggedIn) {
        next('/user-login')
    } else {
        next()
    }
})

export default router
