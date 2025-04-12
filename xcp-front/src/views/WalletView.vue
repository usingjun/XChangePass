<template>
  <div class="wallet-container">
    <div class="wallet-card">
      <h2 class="text-center mb-4">💳 거래 페이지</h2>
      <ul class="nav nav-tabs justify-content-center mb-4">
        <li class="nav-item" @click="tab = 'deposit'">
          <a :class="['nav-link', { active: tab === 'deposit' }]">입금</a>
        </li>
        <li class="nav-item" @click="tab = 'withdraw'">
          <a :class="['nav-link', { active: tab === 'withdraw' }]">출금</a>
        </li>
        <li class="nav-item" @click="tab = 'transfer'">
          <a :class="['nav-link', { active: tab === 'transfer' }]">송금</a>
        </li>
      </ul>

      <div class="mt-4">
        <DepositForm v-if="tab === 'deposit'" />
        <WithdrawForm v-if="tab === 'withdraw'" />
        <TransferForm v-if="tab === 'transfer'" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'

import DepositForm from '@/components/DepositForm.vue'
import WithdrawForm from '@/components/WithdrawForm.vue'
import TransferForm from '@/components/TransferForm.vue'

const router = useRouter()
const auth = useAuthStore()
const { isLoggedIn } = storeToRefs(auth)

const tab = ref('deposit')

onMounted(() => {
  if (!isLoggedIn.value) {
    alert('로그인이 필요합니다.')
    router.push('/user-login')
  }
})
</script>

<style scoped>
.wallet-container {
  width: 100%;
  max-width: 1100px;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem;
  box-sizing: border-box;
}

.wallet-card {
  width: 100%;
  max-width: 3000px;
  padding: 3rem 4rem;
}
</style>
