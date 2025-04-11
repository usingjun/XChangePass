<template>
  <div class="auth-container">
    <div class="auth-card">
      <LoginForm /> <!-- RegisterForm, 홈 컴포넌트 등 대체 가능 -->
    </div>
  </div>
</template>

<script setup>
import LoginForm from '@/components/LoginForm.vue'
</script>

<style scoped>
.auth-container {
  width: 100%;
  max-width: 1100px;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem;
  box-sizing: border-box;
}

.auth-card {
  background-color: white;
  padding: 3rem 4rem;
  border-radius: 1.5rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  width: 200%;
  max-width: 3000px; /* ✅ 960px까지 확장, 원하시면 1000px, 1200px도 가능 */
  min-height: 500px; /* ✅ 세로도 어느 정도 보이게 */
}
</style>
