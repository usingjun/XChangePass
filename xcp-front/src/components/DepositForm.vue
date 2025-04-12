<template>
  <form @submit.prevent="handleDeposit">
    <div class="mb-3">
      <label class="form-label">입금 금액</label>
      <input v-model.number="amount" type="number" class="form-control" min="1" step="0.01" required />
    </div>

    <div class="mb-3">
      <label class="form-label">보유 화폐 (From)</label>
      <select v-model="fromCurrency" class="form-select" required>
        <option disabled value="">선택하세요</option>
        <option v-for="c in currencyOptions" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>

    <div class="mb-3">
      <label class="form-label">입금할 화폐 (To)</label>
      <select v-model="toCurrency" class="form-select" required>
        <option disabled value="">선택하세요</option>
        <option v-for="c in currencyOptions" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>

    <button type="submit" class="btn btn-success">입금</button>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'

axios.defaults.withCredentials = true

const amount = ref(0)
const fromCurrency = ref('')
const toCurrency = ref('')
const currencyOptions = [
  'KRW', 'USD', 'EUR', 'JPY', 'CNY', 'GBP', 'AUD', 'CAD', 'CHF',
  'HKD', 'SGD', 'SEK', 'NOK', 'NZD', 'THB', 'PHP', 'IDR', 'INR', 'MYR', 'VND'
]

const handleDeposit = async () => {
  try {
    const payload = {
      amount: Number(amount.value.toFixed(2)),
      fromCurrency: fromCurrency.value,
      toCurrency: toCurrency.value,
      chargeDatetime: new Date().toISOString()
    }

    await axios.post('http://localhost:8080/api/v1/wallet/charge', payload)

    alert('입금 완료!')
  } catch (e) {
    if (e.response?.status === 401) {
      alert('로그인이 필요합니다.')
    } else {
      alert('입금 실패: ' + (e.response?.data?.message || e.message))
    }
  }
}
</script>
