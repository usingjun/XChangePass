<template>
  <!-- Backdrop -->
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-start justify-center p-4 z-50">
    <!-- Modal -->
    <div class="bg-white w-full max-w-md rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="flex items-center px-4 py-3 border-b">
        <button @click="$emit('close')" class="text-gray-600 text-xl">&larr;</button>
        <h2 class="flex-1 text-center font-semibold text-lg">상세 내역</h2>
      </div>

      <div class="px-6 py-4">
        <!-- 타입 태그 -->
        <span
            class="inline-block px-2 py-1 border rounded-full text-sm text-gray-500 mb-2"
        >{{ typeLabel }}</span>

        <!-- 상점명 -->
        <div class="text-xl font-bold">{{ transaction.merchantName }}</div>

        <!-- 승인 금액 -->
        <div class="text-2xl font-extrabold my-2">
          {{ formatCurrency(transaction.approvedAmount, transaction.approvedCurrency, true) }}
        </div>

        <!-- 후기 버튼 -->
        <button
            class="w-full border border-blue-400 text-blue-600 py-2 rounded-md flex items-center justify-center mb-4"
        >
          <span class="mr-1">👢</span>방문 후기를 남겨주세요.
        </button>

        <!-- 구분선 -->
        <div class="border-t"></div>

        <!-- 메모 입력 -->
        <button
            @click="onEditMemo"
            class="w-full flex justify-between items-center py-3"
        >
          <span class="text-gray-600">메모남기기(최대 20자)</span>
          <span class="text-gray-400">✏️</span>
        </button>

        <div class="border-t"></div>

        <!-- 상세 항목들 -->
        <div
            v-for="row in detailRows"
            :key="row.label"
            class="flex justify-between py-3 border-b last:border-b-0"
        >
          <span class="text-gray-600">{{ row.label }}</span>
          <span class="text-gray-800">{{ row.value }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  transaction: {
    type: Object,
    required: true
  }
})

/** 상단에 표시할 타입 라벨 (결제/취소 등) */
const typeLabel = computed(() => {
  switch (props.transaction.transactionType) {
    case 'PAYMENT':  return '결제'
    case 'CANCEL':   return '취소'
    default:         return props.transaction.transactionType
  }
})

/** 화면에 뿌릴 각 행 데이터 */
const detailRows = computed(() => [
  {
    label: '이용일시',
    value: formatDateTime(props.transaction.transactionTime)
  },
  {
    label: '이용금액',
    value: formatCurrency(props.transaction.approvedAmount, props.transaction.approvedCurrency)
  },
  {
    label: '원화금액',
    value: formatCurrency(props.transaction.krwAmount, 'KRW')
  },
  {
    label: '이용구분',
    value: translateType(props.transaction.transactionType)
  },
  {
    label: '승인번호',
    value: props.transaction.approvalNumber
  },
  {
    label: '거래 후 잔액',
    value: formatCurrency(props.transaction.balanceAfter, props.transaction.approvedCurrency)
  }
])

/** 날짜/시간 포맷 YYYY-MM-DD HH:mm:ss */
function formatDateTime(isoString) {
  const dt = new Date(isoString)
  const p = (n) => n.toString().padStart(2, '0')
  return `${dt.getFullYear()}-${p(dt.getMonth()+1)}-${p(dt.getDate())} ${p(dt.getHours())}:${p(dt.getMinutes())}:${p(dt.getSeconds())}`
}

/** Intl을 이용한 통화 포맷팅
 *  showSign=true 면 맨 앞에 기호(+, -)도 붙입니다.
 */
function formatCurrency(amount, code, showSign = false) {
  const localeMap  = { KRW: 'ko-KR', USD: 'en-US', JPY: 'ja-JP' }
  const symbolMap  = { KRW: '₩', USD: '$',    JPY: '¥'    }
  const locale     = localeMap[code] || 'en-US'
  const symbol     = symbolMap[code] || ''
  const formatted  = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(amount)
  return showSign
      ? `${symbol}${formatted}`
      : `${symbol}${formatted}`
}

/** transactionType → 한글 변환 (이용구분용) */
function translateType(type) {
  switch (type) {
    case 'PAYMENT': return '결제 승인'
    case 'CANCEL':  return '결제 취소'
    default:        return type
  }
}

/** (선택) 메모 편집 핸들러 */
function onEditMemo() {
  // TODO: 메모 입력 모달 띄우기
  console.log('메모 편집 열기')
}
</script>

<style scoped>
/* Tailwind를 쓰지 않는 환경이라면 아래 스타일을 살려주세요 */
.modal-backdrop {
  position: fixed; top: 0; right: 0; bottom: 0; left: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: start; justify-content: center;
  padding: 1rem; z-index: 50;
}
.modal-container {
  background: #fff;
  width: 100%; max-width: 20rem;
  border-radius: 0.5rem;
  overflow: hidden;
}
</style>
