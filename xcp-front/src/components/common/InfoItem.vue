<template>
  <div class="flex justify-between items-center border-b pb-3">
    <div class="w-24 text-gray-500 text-sm">{{ label }}</div>
    <div class="text-sm font-medium break-words text-right">{{ value }}</div>
  </div>
</template>

<script setup>
const props = defineProps({
  label: String,
  value: String,
})
</script>
