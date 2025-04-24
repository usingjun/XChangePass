<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white w-full max-w-2xl p-8 rounded-xl shadow-lg space-y-6">
      <h2 class="text-2xl font-bold text-center">카드 상세 정보</h2>
      <div class="space-y-4">
        <!-- 카드 상세 항목 -->
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">카드 번호</span>
          <span class="font-medium text-gray-800">{{ card.cardNumber }}</span>
        </div>
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">CVC</span>
          <span class="font-medium text-gray-800">{{ card.cvc }}</span>
        </div>
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">카드 타입</span>
          <span class="font-medium text-gray-800">{{ card.cardType }}</span>
        </div>
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">상태</span>
          <span class="font-medium text-gray-800">{{ card.cardStatus }}</span>
        </div>
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">만료일자</span>
          <span class="font-medium text-gray-800">{{ card.expirationDate }}</span>
        </div>
        <div class="flex justify-between text-lg">
          <span class="text-gray-500">발급일자</span>
          <span class="font-medium text-gray-800">{{ card.cardCreateDate }}</span>
        </div>
      </div>
      <button
          class="mt-4 w-full bg-blue-500 hover:bg-blue-600 text-white py-2 rounded font-semibold"
          style="background-color: #60A5FA; color: white;"
          @click="$emit('close')"
      >
        닫기
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  card: Object
})
defineEmits(['close'])
</script>
