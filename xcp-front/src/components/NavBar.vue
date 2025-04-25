<template>
  <nav class="navbar navbar-light bg-white shadow-sm px-4">
    <div class="container d-flex justify-content-between align-items-center">
      <!-- 브랜드명 왼쪽 -->
      <router-link to="/" class="navbar-brand fw-bold">XChangePass</router-link>

      <!-- 로그인 상태에 따라 우상단 버튼 -->
      <div class="d-flex align-items-center">
        <template v-if="isLoggedIn">
          <!-- 마이페이지 버튼 추가 -->
          <router-link to="/my-page" class="btn btn-outline-secondary me-2">
            마이페이지
          </router-link>
          <button class="btn btn-outline-primary" @click="handleLogout">
            로그아웃
          </button>
        </template>
        <template v-else>
          <router-link to="/user-login" class="btn btn-outline-primary me-2">
            로그인
          </router-link>
          <router-link to="/register" class="btn btn-outline-secondary">
            회원가입
          </router-link>
        </template>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/userApi'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

const auth = useAuthStore()
const router = useRouter()
const { isLoggedIn } = storeToRefs(auth)

const handleLogout = async () => {
  try {
    const res = await logout()
    // axios 등으로 돌아오는 response 는 보통 .status로 확인하므로 필요에 맞게 조정하세요
    if (res.status === 200) {
      auth.logout()
      router.push('/user-login')
    } else {
      alert('로그아웃 실패')
    }
  } catch (err) {
    console.error('로그아웃 오류', err)
    alert('에러 발생')
  }
}
</script>
