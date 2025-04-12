<template>
  <div class="flex flex-col gap-6">
    <!-- 환영 카드 -->
    <section class="card">
      <h2 class="text-center mb-3">🎉 환영합니다!</h2>
      <p class="text-center text-muted mb-3">
        로그인에 성공하셨습니다.<br />
        XChangePass 서비스를 자유롭게 이용하실 수 있습니다.
      </p>
      <hr />
      <div class="flex justify-center gap-3 mt-3">
        <button class="btn-main" @click="goToTransfer">송금하기</button>
        <button class="btn btn-outline-secondary">거래 내역</button>
      </div>
    </section>

    <!-- 내 지갑 카드 -->
    <section class="card">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold flex items-center gap-1">
          내 지갑
          <span class="text-gray-400 text-sm">ⓘ</span>
        </h2>
        <button class="text-gray-400 text-sm">이용내역 ></button>
      </div>

      <div v-if="loadingWallet" class="text-center text-gray-400">지갑 정보를 불러오는 중...</div>

      <div v-else class="space-y-3">
        <div v-for="item in walletList" :key="item.country" class="flex justify-between items-center">
          <div class="flex items-center gap-2">
            <span>{{ item.flag }}</span>
            <span class="font-medium">{{ item.country }}</span>
          </div>
          <span class="font-semibold">{{ item.amount }}</span>
        </div>
      </div>

      <button @click="goToTransfer" class="btn-main mt-6">
        충전하기
      </button>
    </section>

    <!-- 환율 조회 카드 -->
    <section class="card">
      <h2 class="text-lg font-bold mb-4">환율 조회</h2>

      <div class="flex flex-col gap-3">
        <select v-model="baseCurrency" class="select-box">
          <option value="USD">USD</option>
          <option value="KRW">KRW</option>
          <option value="JPY">JPY</option>
        </select>

        <select v-model="targetCurrency" class="select-box">
          <option value="USD">USD</option>
          <option value="KRW">KRW</option>
          <option value="JPY">JPY</option>
        </select>

        <button @click="fetchRate" class="btn-main">환율 조회</button>

        <div v-if="loading" class="text-center text-gray-400 mt-2">불러오는 중...</div>
        <div v-else-if="rate" class="text-center text-lg font-bold mt-2">
          {{ baseCurrency }} → {{ targetCurrency }} : {{ rate }}
        </div>
      </div>
    </section>

    <!-- 환율 정보 카드 -->
    <section class="card">
      <h2 class="text-lg font-bold mb-4">환율 정보 (KRW 기준)</h2>

      <div v-if="loadingExchange" class="text-center text-gray-400">
        환율 정보를 불러오는 중...
      </div>

      <div v-else class="flex flex-col gap-3">
        <div
            v-for="item in exchangeList"
            :key="item.country"
            class="bg-gray-50 rounded-xl p-4 flex justify-between items-center shadow"
        >
          <div class="flex items-center gap-2">
            <div class="text-2xl">{{ item.flag }}</div>
            <div class="text-sm text-gray-600">{{ item.country }}</div>
          </div>
          <div class="text-right">
            <div class="text-lg font-bold">{{ (item.rate * 1).toLocaleString() }}원</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from '@/api/axios'
import { fetchExchangeRateByCountry } from '@/api/exchange'

const router = useRouter()

const baseCurrency = ref('USD')
const targetCurrency = ref('KRW')
const rate = ref(null)
const loading = ref(false)

const walletList = ref([])
const loadingWallet = ref(true)

const exchangeList = ref([])
const loadingExchange = ref(true)

const fetchWallet = async () => {
  try {
    const res = await axios.get('/wallet')
    walletList.value = res.data
  } catch (e) {
    console.error('지갑 정보 불러오기 실패', e)
  } finally {
    loadingWallet.value = false
  }
}

const fetchRate = async () => {
  loading.value = true
  try {
    const res = await fetchExchangeRateByCountry(baseCurrency.value, targetCurrency.value)
    rate.value = res.data.conversion_rates[targetCurrency.value]
  } catch (e) {
    console.error('환율 조회 실패', e)
  } finally {
    loading.value = false
  }
}

const fetchExchangeKRW = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/exchange-rate/KRW')
    const rates = res.data.conversion_rates
    exchangeList.value = [
      { country: '일본', flag: '🇯🇵', rate: rates.JPY },
      { country: '유럽', flag: '🇪🇺', rate: rates.EUR },
      { country: '미국', flag: '🇺🇸', rate: rates.USD }
    ]
  } catch (e) {
    console.error('환율 정보 불러오기 실패', e)
  } finally {
    loadingExchange.value = false
  }
}

const goToTransfer = () => {
  router.push('/transfer')
}

onMounted(() => {
  fetchWallet()
  fetchExchangeKRW()
})
</script>

<style scoped>
.card {
  background-color: white;
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.btn-main {
  background-color: #2563eb;
  color: white;
  padding: 0.5rem 1rem;
  width: 100%;
  border-radius: 0.5rem;
  font-weight: 500;
  transition: background-color 0.2s;
}

.btn-main:hover {
  background-color: #1e40af;
}

.select-box {
  border: 1px solid #ccc;
  padding: 0.5rem;
  border-radius: 0.5rem;
  width: 100%;
}
</style>
