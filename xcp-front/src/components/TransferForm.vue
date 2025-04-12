<template>
  <form @submit.prevent="handleTransfer">
    <div class="mb-3">
      <label class="form-label">받는 분 이름</label>
      <input v-model="receiverName" type="text" class="form-control" required />
    </div>

    <div class="mb-3">
      <label class="form-label">받는 분 전화번호</label>
      <input v-model="receiverPhoneNumber" type="tel" class="form-control" required />
    </div>

    <div class="mb-3">
      <label class="form-label">송금 금액</label>
      <input v-model.number="transferAmount" type="number" class="form-control" min="1" required />
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
      <label class="form-label">보낼 화폐</label>
      <select v-model="toCurrency" class="form-select" required>
        <option disabled value="">선택하세요</option>
        <option v-for="currency in currencyOptions" :key="currency" :value="currency">
          {{ currency }}
        </option>
      </select>
    </div>

    <div class="mb-3" v-if="transferType === 'SCHEDULED'">
      <label class="form-label">예약 송금 시간</label>
      <input
          v-model="transferDatetime"
          type="datetime-local"
          class="form-control"
          :min="minDatetime"
          :max="maxDatetime"
          required
      />
    </div>

    <div class="mb-3">
      <label class="form-label">송금 타입</label>
      <select v-model="transferType" class="form-select" required>
        <option value="GENERAL">일반 송금</option>
        <option value="SCHEDULED">예약 송금</option>
      </select>
    </div>

    <button type="submit" class="btn btn-primary">송금</button>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

axios.defaults.withCredentials = true

const receiverName = ref('')
const receiverPhoneNumber = ref('')
const transferAmount = ref(0)
const fromCurrency = ref('')
const toCurrency = ref('')
const transferDatetime = ref('')
const transferType = ref('GENERAL')

// 예약 송금 가능한 시간 범위
const minDatetime = ref('')
const maxDatetime = ref('')

// 전체 통화 옵션
const currencyOptions = ['KRW', 'USD', 'EUR', 'JPY']

// 서버에서 조회한 사용자 보유 화폐
const balances = ref([])

onMounted(async () => {
  try {
    // 잔액 조회
    const res = await axios.get('http://localhost:8080/api/v1/wallet/balance')
    balances.value = res.data
  } catch (e) {
    console.error('잔액 조회 실패:', e)
    alert('잔액 정보를 불러오는 데 실패했습니다.')
  }

  // 예약 송금 시간 제한 (현재 ~ 1년 뒤)
  const now = new Date()
  const oneYearLater = new Date()
  oneYearLater.setFullYear(now.getFullYear() + 1)

  const format = (date) => date.toISOString().slice(0, 16) // 'YYYY-MM-DDTHH:mm'

  minDatetime.value = format(now)
  maxDatetime.value = format(oneYearLater)
})

const handleTransfer = async () => {
  try {
    const payload = {
      receiverName: receiverName.value,
      receiverPhoneNumber: receiverPhoneNumber.value,
      transferAmount: transferAmount.value,
      fromCurrency: fromCurrency.value,
      toCurrency: toCurrency.value,
      transferDatetime:
          transferType.value === 'SCHEDULED'
              ? new Date(transferDatetime.value).toISOString()
              : new Date().toISOString(),
      transferType: transferType.value
    }

    console.log('[송금 요청 payload]', payload)

    const endpoint = transferType.value === 'SCHEDULED'
        ? '/api/v1/wallet/transfer-schedule'
        : '/api/v1/wallet/transfer'

    await axios.put(`http://localhost:8080${endpoint}`, payload)

    alert(`${transferType.value === 'SCHEDULED' ? '예약' : '일반'} 송금 완료!`)
  } catch (e) {
    if (e.response?.status === 401) {
      alert('로그인이 필요합니다.')
    } else {
      const data = e.response?.data
      if (data?.validation) {
        const messages = Object.entries(data.validation)
            .map(([field, msg]) => `${field}: ${msg}`)
            .join('\n')
        alert(`유효성 오류:\n${messages}`)
      } else {
        alert('송금 실패: ' + (data?.message || e.message))
      }
    }
  }
}
</script>
