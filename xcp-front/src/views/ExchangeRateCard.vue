<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api/axios'
import currencyNameMap from '@/assets/currency-name.json'
import { fetchExchangeRateByCountry } from '@/api/exchange'

const baseCurrency = ref('USD')
const targetCurrency = ref('KRW')
const rate = ref(null)
const loading = ref(false)

const currencyList = ref([])

// 환율 조회
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

// 통화 목록 조회
const fetchCurrencyList = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/exchange-rate/KRW')
    currencyList.value = Object.keys(res.data.conversion_rates)
  } catch (e) {
    console.error('통화 목록 불러오기 실패', e)
  }
}

onMounted(() => {
  fetchCurrencyList()
})
</script>

<template>
  <!-- 네가 작성한 환율 조회 카드 그대로 사용 -->
</template>
