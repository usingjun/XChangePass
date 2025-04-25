<template>
  <button
      @click="$emit('click')"
      class="flex items-center px-5 py-5 bg-white rounded-xl shadow hover:bg-gray-50 transition"
  >
    <!-- 아이콘 -->
    <component :is="icons[icon]" class="w-7 h-7 text-gray-500" />

    <!-- 텍스트 -->
    <span class="ml-4 text-base font-medium text-gray-800">
      {{ title }}
    </span>

    <!-- 우측 화살표 -->
    <ChevronRightIcon class="ml-auto w-5 h-5 text-gray-400" />
  </button>
</template>

<script setup>
import { defineProps } from 'vue'
import { CreditCardIcon, DocumentTextIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'

const props = defineProps({
  icon:  { type: String, required: true },
  title: { type: String, required: true },
})

const icons = {
  CreditCardIcon,
  DocumentTextIcon,
}
</script>
