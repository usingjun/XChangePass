<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from '@/api/axios'
import currencyNameMap from '@/assets/currency-name.json'
import { fetchExchangeRateByCountry } from '@/api/exchange'

const searchKeyword = ref('')
const selectedCurrency1 = ref('')
const selectedCurrency2 = ref('')
const rate = ref(null)
const loading = ref(false)
const currencyList = ref([])

const filteredCurrencyList = computed(() =>
    currencyList.value.filter(
        (currency) =>
            currencyNameMap[currency]?.includes(searchKeyword.value) ||
            currency.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
)

const selectCurrency = (currency) => {
  if (!selectedCurrency1.value) {
    selectedCurrency1.value = currency
  } else {
    selectedCurrency2.value = currency
  }
  searchKeyword.value = ''
}

const fetchCurrencyList = async () => {
  const res = await axios.get('http://localhost:8080/api/exchange-rate/KRW')
  currencyList.value = Object.keys(res.data.conversion_rates)
}

// 환율 조회
const fetchRate = async () => {
  if (!selectedCurrency1.value || !selectedCurrency2.value) {
    alert('두 통화를 모두 선택해주세요!')
    return
  }

  loading.value = true
  try {
    const res = await fetchExchangeRateByCountry(selectedCurrency1.value, selectedCurrency2.value)
    rate.value = res.data.conversion_rates[selectedCurrency2.value]
  } catch (e) {
    console.error('환율 조회 실패', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchCurrencyList()
})
</script>

<template>
  <section class="card">
    <h2 class="text-lg font-bold mb-4">환율 조회</h2>

    <!-- 검색창 -->
    <input
        v-model="searchKeyword"
        type="text"
        placeholder="국가 또는 통화 검색"
        class="select-box"
    />

    <!-- 검색 결과 -->
    <ul v-if="searchKeyword" class="border rounded p-2 max-h-40 overflow-y-auto">
      <li
          v-for="currency in filteredCurrencyList"
          :key="currency"
          @click="selectCurrency(currency)"
          class="p-1 cursor-pointer hover:bg-gray-100"
      >
        {{ currencyNameMap[currency] || currency }}
      </li>
    </ul>

    <!-- 선택된 통화 -->
    <div class="mt-4 space-y-2">
      <div>첫 번째 통화: {{ currencyNameMap[selectedCurrency1] || selectedCurrency1 }}</div>
      <div>두 번째 통화: {{ currencyNameMap[selectedCurrency2] || selectedCurrency2 }}</div>
    </div>

    <!-- 환율 조회 버튼 -->
    <button @click="fetchRate" class="btn-main mt-4 w-full">
      환율 조회
    </button>

    <!-- 결과 -->
    <div v-if="loading" class="text-center text-gray-400 mt-2">
      불러오는 중...
    </div>

    <div v-else-if="rate" class="text-center text-lg font-bold mt-2">
      {{ currencyNameMap[selectedCurrency1] || selectedCurrency1 }}
      →
      {{ currencyNameMap[selectedCurrency2] || selectedCurrency2 }}
      : {{ rate }}
    </div>
  </section>
</template>
