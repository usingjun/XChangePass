<template>
  <div>
    <!-- 비밀번호 확인 모달 -->
    <PasswordConfirmModal
        v-if="showPasswordModal"
        :show="showPasswordModal"
        @success="confirmAndWithdraw"
        @close="showPasswordModal = false"
    />

    <form @submit.prevent="onSubmitClicked">
      <div class="mb-3">
        <label class="form-label">출금 금액</label>
        <input v-model.number="amount" type="number" class="form-control" min="1" step="0.01" required />
      </div>

      <div class="mb-3">
        <label class="form-label">보유 화폐</label>
        <select v-model="fromCurrency" class="form-select" required>
          <option disabled value="">선택하세요</option>
          <option v-for="balance in balances" :key="balance.currency" :value="balance.currency">
            {{ balance.currency }} (잔액: {{ balance.balance }})
          </option>
        </select>
      </div>

      <div class="mb-3">
        <label class="form-label">출금할 화폐 (To)</label>
        <select v-model="toCurrency" class="form-select" required>
          <option disabled value="">선택하세요</option>
          <option v-for="c in currencyOptions" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>

      <div v-if="convertedAmount" class="text-muted mt-2">
        환율 적용 금액: <strong>{{ convertedAmount }}</strong> {{ toCurrency }}
      </div>

      <button type="submit" class="btn btn-warning">출금</button>
    </form>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import axios from 'axios'
import { useExchangeCalculator } from '@/api/useExchangeCalculator.js'
import PasswordConfirmModal from '@/components/PasswordConfirmModal.vue'

axios.defaults.withCredentials = true

const amount = ref(0)
const fromCurrency = ref('')
const toCurrency = ref('')
const showPasswordModal = ref(false)

const currencyOptions = [
  'KRW', 'USD', 'EUR', 'JPY', 'CNY', 'GBP', 'AUD', 'CAD', 'CHF',
  'HKD', 'SGD', 'SEK', 'NOK', 'NZD', 'THB', 'PHP', 'IDR', 'INR', 'MYR', 'VND'
]
const balances = ref([])


const { convertedAmount } = useExchangeCalculator(fromCurrency, toCurrency, amount)

onMounted(async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/v1/wallet/balance')
    balances.value = res.data
  } catch (e) {
    alert('잔액 정보를 불러오는 데 실패했습니다.')
  }
})

const onSubmitClicked = () => {
  showPasswordModal.value = true
}

const confirmAndWithdraw = async () => {
  showPasswordModal.value = false

  try {
    const payload = {
      amount: Number(amount.value.toFixed(2)),
      fromCurrency: fromCurrency.value,
      toCurrency: toCurrency.value,
      chargeDatetime: new Date().toISOString()
    }

    await axios.put('http://localhost:8080/api/v1/wallet/withdraw', payload)
    alert('출금 완료!')
  } catch (e) {
    if (e.response?.status === 401) {
      alert('로그인이 필요합니다.')
    } else {
      alert('출금 실패: ' + (e.response?.data?.message || e.message))
    }
  }
}
</script>
