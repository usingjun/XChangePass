<script setup>
import { ref, computed, onMounted } from 'vue'
import currencyNameMap from '@/assets/currency-name.json'
import { createExchangeTransaction, executeExchangeTransaction } from '@/api/exchange'
import axios from '@/api/axios'

// 상태 변수
const baseCurrency = ref('KRW')
const targetCurrency = ref('USD')
const amount = ref(0)

const currencyList = ref([]) // 전체 통화
const searchKeyword = ref('') // 보유 통화 검색어
const searchTargetKeyword = ref('') // 목표 통화 검색어

const receivedAmount = ref(null)
const transactionId = ref(null)
const loading = ref(false)

// 필터링된 select 목록
const filteredCurrencyList = computed(() =>
    currencyList.value.filter(currency =>
        (currencyNameMap[currency] || currency).includes(searchKeyword.value)
    )
)

const filteredTargetCurrencyList = computed(() =>
    currencyList.value.filter(currency =>
        (currencyNameMap[currency] || currency).includes(searchTargetKeyword.value)
    )
)

// 통화 목록 조회
const fetchCurrencyList = async () => {
  const res = await axios.get('http://localhost:8080/api/exchange-rate/KRW')
  currencyList.value = Object.keys(res.data.conversion_rates)
}

// 거래 생성
const createTransaction = async () => {
  if (!amount.value || amount.value <= 0) {
    alert('금액을 입력하세요.')
    return
  }

  loading.value = true
  try {
    const res = await createExchangeTransaction({
      fromCurrency: baseCurrency.value,
      toCurrency: targetCurrency.value,
      amount: amount.value
    })
    transactionId.value = res.data.transactionId
    receivedAmount.value = res.data.receivedAmount
  } catch (e) {
    console.error('거래 생성 실패', e)
    alert(e.response?.data?.message || '거래 생성 실패')
  } finally {
    loading.value = false
  }
}

// 거래 실행
const executeTransaction = async () => {
  if (!transactionId.value) {
    alert('먼저 거래를 생성해주세요.')
    return
  }

  try {
    await executeExchangeTransaction(transactionId.value)
    alert('환전 성공!')
    location.reload()
  } catch (e) {
    console.error('거래 실행 실패', e)
    alert(e.response?.data?.message || '환전 실패')
  }
}

onMounted(() => {
  fetchCurrencyList()
})
</script>

<template>
  <section class="card">
    <h2 class="text-lg font-bold mb-4">환전하기</h2>

    <div class="flex flex-col gap-3">
      <!-- 보유 통화 -->
      <div>
        보유 통화:
        <input v-model="searchKeyword" placeholder="보유 통화 검색" class="select-box" />
        <select v-model="baseCurrency" class="select-box">
          <option v-for="currency in filteredCurrencyList" :key="currency" :value="currency">
            {{ currencyNameMap[currency] || currency }}
          </option>
        </select>
      </div>

      <!-- 목표 통화 -->
      <div>
        목표 통화:
        <input v-model="searchTargetKeyword" placeholder="목표 통화 검색" class="select-box" />
        <select v-model="targetCurrency" class="select-box">
          <option v-for="currency in filteredTargetCurrencyList" :key="currency" :value="currency">
            {{ currencyNameMap[currency] || currency }}
          </option>
        </select>
      </div>

      <!-- 금액 입력 -->
      <input v-model="amount" type="number" placeholder="금액 입력" class="select-box" />

      <!-- 환전 금액 조회 -->
      <button @click="createTransaction" class="btn-main">
        환전 금액 조회
      </button>

      <!-- 환전 예상 금액 -->
      <div v-if="receivedAmount" class="text-center text-lg font-bold mt-2">
        예상 수령 금액: {{ receivedAmount.toLocaleString() }} {{ targetCurrency }}
      </div>

      <!-- 환전 실행 -->
      <button v-if="transactionId" @click="executeTransaction" class="btn-main mt-4">
        환전 실행
      </button>
    </div>
  </section>
</template>
