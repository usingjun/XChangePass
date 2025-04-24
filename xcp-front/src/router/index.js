import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import HomeView from '@/views/HomeView.vue'
import MyPageView from "@/views/MyPageView.vue";
import MyInfoView from "@/views/MyInfoView.vue";
import CardManager from "@/views/CardManager.vue";
import CardTransactions from "@/views/CardTransactions.vue";

const routes = [
    { path: '/', name: 'Home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/user-login', name: 'Login', component: LoginView },
    { path: '/my-page', name: "MyPage", component: MyPageView},
    { path: '/my-info', name: "MyInfo", component: MyInfoView},
    { path: '/card', name: "CardManager", component: CardManager},
    { path: '/cardTransaction', name: "CardTransaction", component: CardTransactions},
    { path: '/register', name: 'Register', component: RegisterView },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isLoggedIn) {
        next('/user-login')
    } else {
        next()
    }
})

export default router
