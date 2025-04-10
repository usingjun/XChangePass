<template>
  <div id="app">
    <NavBar />
    <main class="main-container">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import NavBar from '@/components/NavBar.vue'
</script>

<style>
body {
  background-color: #f8f9fa;
  font-family: 'Noto Sans KR', sans-serif;
  margin: 0;
  padding: 0;
}

#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-container {
  flex: 1;
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  padding: 4rem 2rem;
  width: 100vw;
  box-sizing: border-box;
}

.main-container > * {
  width: 100%;
  max-width: 1200px; 
  padding: 2rem;
  background-color: #fff;
  border-radius: 1rem;
  box-shadow: 0 0 10px rgba(0,0,0,0.05);
}
</style>
