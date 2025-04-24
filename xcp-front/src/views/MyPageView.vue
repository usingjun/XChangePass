<template>
  <div class="p-6 space-y-6">
    <h2 class="text-2xl font-bold mb-6 text-center">마이페이지</h2>

    <!-- 닉네임 영역 -->
    <div v-if="user" class="text-center mb-6">
      <div class="text-2xl font-bold mb-4">{{ user.userNickName }} 님</div>
      <button
          @click="router.push({ name: 'MyInfo', state: { user } })"
          class="w-full py-3 border border-blue-500 text-blue-500 font-semibold rounded-xl hover:bg-blue-50 transition"
      >
        내 정보 관리
      </button>
    </div>

    <!-- 메뉴 버튼 -->
    <div class="space-y-4 mt-6">
      <!-- 카드 관리 -->
      <button
          @click="router.push('/card')"
          class="flex items-center justify-start w-full px-5 py-3 bg-white border border-gray-300 rounded-lg shadow hover:bg-gray-100 transition"
      >
        <CreditCardIcon style="width: 32px; height: 32px;" class="text-gray-600 flex-shrink-0" />
        <span class="ml-3 text-base font-medium text-gray-800">카드 관리</span>
      </button>

      <!-- 이용 내역 -->
      <button
          @click="router.push('/cardTransaction')"
          class="flex items-center justify-start w-full px-5 py-3 bg-white border border-gray-300 rounded-lg shadow hover:bg-gray-100 transition"
      >
        <DocumentTextIcon style="width: 32px; height: 32px;" class="text-gray-600 flex-shrink-0" />
        <span class="ml-3 text-base font-medium text-gray-800">이용 내역</span>
      </button>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { CreditCardIcon, DocumentTextIcon } from '@heroicons/vue/24/outline'
import { getMyInfo } from '@/api/userApi.js'

const user = ref(null)
const router = useRouter()

onMounted(async () => {
  try {
    const res = await getMyInfo()
    user.value = res.data
  } catch (e) {
    console.error('내 정보 조회 실패', e)
  }
})
</script>
