<template>
  <div class="auth-container">
    <div class="auth-card">
      <RegisterForm />
    </div>
  </div>
</template>

<script setup>
import RegisterForm from '@/components/RegisterForm.vue'
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
  width: 100%;
  max-width: 3000px; /* 필요시 1100~1400px 등 조정 가능 */
  min-height: 500px;
}
</style>
