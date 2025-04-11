<template>
  <div class="container mt-5" style="max-width: 500px;">
    <h2 class="mb-4 text-center">로그인</h2>

    <form @submit.prevent="login">
      <div class="mb-3">
        <label class="form-label">이메일</label>
        <input v-model="email" type="email" class="form-control" placeholder="example@mail.com" required />
      </div>

      <div class="mb-4">
        <label class="form-label">비밀번호</label>
        <input v-model="password" type="password" class="form-control" placeholder="비밀번호 입력" required />
      </div>

      <button type="submit" class="btn btn-primary w-100">로그인</button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const email = ref('')
const password = ref('')

const login = async () => {
  try {
    const response = await fetch('http://localhost:8080/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        userEmail: email.value,
        password: password.value
      })
    })

    // ✅ 먼저 텍스트로 받아오기
    const text = await response.text()

    let result = {}
    if (text) {
      try {
        result = JSON.parse(text)
      } catch (e) {
        console.error('JSON 파싱 실패:', e)
      }
    }

    if (response.ok) {
      alert('로그인 성공!')
      auth.login() // ✅ 반드시 여기서 호출
      router.push('/')
    } else {
      alert(`로그인 실패: ${result.message || '알 수 없는 오류'}`)
    }
  } catch (err) {
    console.error('요청 오류:', err)
    alert('서버 통신 중 오류 발생')
  }
}
</script>
