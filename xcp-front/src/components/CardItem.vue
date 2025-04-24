<template>
  <div class="p-4 border rounded-xl shadow space-y-3">
    <div class="flex justify-between items-center">
      <div class="font-bold text-lg">
        {{ cardType === 'MOBILE' ? '모바일 카드' : '실물 카드' }}
        <span class="text-sm bg-blue-100 text-blue-800 ml-2 px-2 py-0.5 rounded">플래티늄</span>
      </div>
      <div class="text-sm text-gray-600">{{ maskedCardNumber }}</div>
    </div>

    <div class="flex items-center space-x-2">
      <label class="text-sm font-medium">카드활성화</label>
      <input
          type="checkbox"
          :checked="isActive"
          @change="toggleStatus"
          class="form-checkbox"
      />
    </div>

    <button
        :disabled="false"
        class="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 rounded shadow transition disabled:opacity-50 disabled:cursor-not-allowed"
        style="background-color: #60A5FA; color: white;"
        @click="onViewInfo"
    >
      카드 정보 보기
    </button>
  </div>
</template>

<script setup>
import { computed, defineProps, defineEmits } from 'vue'

// 타입을 정확히 정의합니다.
const props = defineProps({
  cardId: {
    type: Number,
    required: true
  },
  cardType: {
    type: String,
    required: true
  },
  cardStatus: {
    type: String,
    required: true
  },
  maskedCardNumber: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['toggle-status', 'view-info'])

const isActive = computed(() => props.cardStatus === 'ACTIVE')

const toggleStatus = (event) => {
  const newStatus = event.target.checked ? 'ACTIVE' : 'INACTIVE'
  emit('toggle-status', {
    cardType: props.cardType,
    status: newStatus
  })
}

// 카드 정보 보기 버튼 클릭 시 emit 이벤트 발생
const onViewInfo = () => {
  console.log(props)
  emit('view-info', props.cardId)
}
</script>
