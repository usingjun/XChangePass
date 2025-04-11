<template>
  <form @submit.prevent="register" class="container mt-5" style="max-width: 600px;">
    <h2 class="mb-4 text-center">회원가입</h2>

    <div class="mb-3">
      <label class="form-label">이메일</label>
      <input v-model="userEmail" type="email" class="form-control" placeholder="example@mail.com" required />
    </div>

    <div class="mb-3">
      <label class="form-label">비밀번호</label>
      <input v-model="userPwd" type="password" class="form-control" placeholder="비밀번호" required />
    </div>

    <div class="mb-3">
      <label class="form-label">이름</label>
      <input v-model="userName" type="text" class="form-control" placeholder="홍길동" required />
    </div>

    <div class="mb-3">
      <label class="form-label">전화번호</label>
      <input v-model="userPhoneNumber" type="tel" class="form-control" placeholder="010-0000-0000" required />
    </div>

    <div class="mb-3">
      <label class="form-label">성별</label>
      <select v-model="userSex" class="form-select" required>
        <option disabled value="">성별 선택</option>
        <option value="MALE">남성</option>
        <option value="FEMALE">여성</option>
      </select>
    </div>

    <div class="mb-4">
      <label class="form-label">지갑 비밀번호 (숫자 4자리)</label>
      <input v-model="walletPassword" type="password" class="form-control" required />
    </div>

    <button type="submit" class="btn btn-success w-100">가입하기</button>
  </form>
</template>

<script setup>
import { ref } from 'vue'

const userEmail = ref('')
const userPwd = ref('')
const userName = ref('')
const userPhoneNumber = ref('')
const userSex = ref('')
const walletPassword = ref('')

const register = async () => {
  const payload = {
    userEmail: userEmail.value,
    userPwd: userPwd.value,
    userName: userName.value,
    userPhoneNumber: userPhoneNumber.value,
    userSex: userSex.value,
    walletPassword: walletPassword.value
  }

  try {
    const response = await fetch('http://localhost:8080/api/v1/user/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const text = await response.text()

    let result = {}
    try {
      result = text ? JSON.parse(text) : {}
    } catch (e) {
      console.error('JSON 파싱 오류:', e)
    }

    if (response.ok) {
      alert('회원가입 성공!')
    } else {
      alert(`회원가입 실패: ${result.message || '서버 오류'}`)
    }

  } catch (err) {
    console.error(err)
    alert('서버 통신 오류')
  }
}
</script>
