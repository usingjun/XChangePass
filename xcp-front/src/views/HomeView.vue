<template>
  <div class="home-container">
    <div class="welcome-card">
      <h2 class="text-center mb-3">🎉 환영합니다!</h2>
      <p class="text-center text-muted mb-3">
        로그인에 성공하셨습니다.<br />
        XChangePass 서비스를 자유롭게 이용하실 수 있습니다.
      </p>
      <hr />
      <div class="d-flex justify-content-center gap-3 mt-3">
        <button class="btn btn-primary">송금하기</button>
        <button class="btn btn-outline-secondary">거래 내역</button>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.home-container {
  width: 100%;
  max-width: 1100px;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem;
  box-sizing: border-box;
}

.welcome-card {
  background-color: white;
  padding: 3rem 4rem;
  border-radius: 1.5rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 3000px;
  min-height: 500px;
}
</style>
