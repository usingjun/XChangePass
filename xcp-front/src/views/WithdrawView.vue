<template>
  <div class="auth-container">
    <div class="auth-card">
      <h2 class="text-center mb-4">💸 출금하기</h2>
      <WithdrawForm />
    </div>
  </div>
</template>

<script setup>
import WithdrawForm from '@/components/WithdrawForm.vue'
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 2rem;
}
.auth-card {
  background-color: white;
  padding: 2.5rem;
  border-radius: 1rem;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  width: 100%;
  max-width: 600px;
}
</style>
