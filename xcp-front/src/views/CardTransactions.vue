<template>
  <div class="p-6 space-y-6 max-w-2xl">
    <h2 class="text-2xl font-bold text-center">이용 내역</h2>

    <div v-if="transactions && transactions.length" class="space-y-4">
      <div
          v-for="transaction in transactions"
          :key="transaction.transactionId"
          class="border-b py-4"
      >
        <div class="flex justify-between">
          <div class="font-medium">{{ transaction.merchantName }}</div>
          <div class="text-sm text-gray-500">{{ transaction.transactionTime }}</div>
        </div>
        <div class="text-lg font-semibold">
          {{ transaction.approvedAmount }} {{ transaction.approvedCurrency }}
        </div>
        <button
            @click="viewTransactionDetail(transaction.transactionId)"
            class="text-blue-600 text-sm"
        >
          거래 상세 보기
        </button>
      </div>
    </div>

    <div v-else class="text-center text-gray-500">
      거래 내역이 없습니다.
    </div>

    <CardTransactionDetailModal
        v-if="selectedTransaction"
        :transaction="selectedTransaction"
        @close="selectedTransaction = null"
    />

    <div v-if="nextCursor" class="text-center mt-4">
      <button
          @click="fetchMoreTransactions"
          class="w-full py-3 bg-blue-500 text-white font-semibold rounded-md"
      >
        더 보기
      </button>
    </div>
  </div>
  <CardTransactionDetailModal
      v-if="selectedTransaction"
      :transaction="selectedTransaction"
      @close="selectedTransaction = null"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCardTransactions, getCardTransactionDetail } from '@/api/transactionApi'
import CardTransactionDetailModal from '@/components/CardTransactionDetailModal.vue'

const transactions = ref([])
const selectedTransaction = ref(null)
const nextCursor = ref(null)

const fetchTransactions = async () => {
  try {
    const res = await getCardTransactions()
    transactions.value = res.data.data
    nextCursor.value = res.data.nextCursor
  } catch (e) {
    console.error('거래 내역 조회 실패', e)
  }
}

const viewTransactionDetail = async (transactionId) => {
  try {
    const res = await getCardTransactionDetail(transactionId)
    selectedTransaction.value = res.data
  } catch (e) {
    console.error('거래 상세 정보 조회 실패', e)
  }
}

const fetchMoreTransactions = async () => {
  if (!nextCursor.value) return
  try {
    const res = await getCardTransactions(nextCursor.value)
    transactions.value.push(...res.data.data)
    nextCursor.value = res.data.nextCursor
  } catch (e) {
    console.error('더 보기 실패', e)
  }
}

onMounted(fetchTransactions)
</script>
