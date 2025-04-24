<template>
  <div class="p-6 space-y-6 max-w-2xl">
    <h2 class="text-2xl font-bold text-center">카드관리</h2>

    <!-- 실물 카드 -->
    <div class="p-6 border rounded-xl bg-white shadow-md space-y-12">
      <div class="font-semibold text-lg">실물 카드</div>
      <div v-if="physicalCard">
        <CardItem
            :card-id="physicalCard.cardId"
            :card-type="physicalCard.cardType"
            :card-status="physicalCard.cardStatus"
            :masked-card-number="physicalCard.maskedCardNumber"
            @toggle-status="handleStatusToggle"
            @view-info="handleViewInfo"
        />
      </div>
      <div v-else class="text-center">
        <p class="text-gray-600 mb-3">보유한 실물 카드가 없습니다.</p>
        <button
            @click="onClickGeneratePhysicalCard"
            class="w-full py-3 font-semibold rounded-md shadow-md transition hover:opacity-90"
            style="background-color: #60A5FA; color: white;"
        >
          실물 카드 발급하기
        </button>
      </div>
    </div>

    <!-- 모바일 카드 -->
    <div class="p-6 border rounded-xl bg-white shadow-md space-y-12">
      <div class="font-semibold text-lg">모바일 카드</div>
      <div v-for="card in mobileCards" :key="card.cardId">
        <CardItem
            :card-id="card.cardId"
            :card-type="card.cardType"
            :card-status="card.cardStatus"
            :masked-card-number="card.maskedCardNumber"
            @toggle-status="handleStatusToggle"
            @view-info="handleViewInfo"
        />
      </div>
    </div>

    <!-- 비밀번호 변경 버튼 -->
    <div class="text-center pt-2">
      <button
          class="w-full max-w-md mx-auto py-3 font-semibold rounded-lg shadow-md transition hover:opacity-90"
          style="background-color: #60A5FA; color: white;"
          @click="onChangePin"
      >
        비밀번호 변경
      </button>
    </div>
  </div>

  <!-- 카드 상세 정보 모달 -->
  <CardDetailModal
      v-if="selectedCardDetail"
      :card="selectedCardDetail"
      @close="selectedCardDetail = null"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCardList, changeCardStatus, generatePhysicalCard, getCardDetail } from '@/api/card'
import CardItem from '@/components/CardItem.vue'
import CardDetailModal from '@/components/CardDetailModal.vue'

const cards = ref([])
const physicalCard = ref(null)
const mobileCards = ref([]) // 모바일 카드 배열로 관리
const selectedCardDetail = ref(null)

const fetchCards = async () => {
  try {
    const res = await getCardList()
    cards.value = res.data
    physicalCard.value = cards.value.find(c => c.cardType === 'PHYSICAL') || null
    mobileCards.value = cards.value.filter(c => c.cardType === 'MOBILE') // 모바일 카드들 필터링

    console.log('Physical Card:', physicalCard.value)
    console.log('Mobile Cards:', mobileCards.value)
  } catch (e) {
    console.error('카드 목록 조회 실패:', e)
  }
}

const handleStatusToggle = async ({ cardType, status }) => {
  try {
    await changeCardStatus({ cardType, status })
    await fetchCards()
  } catch (e) {
    console.error('카드 상태 변경 실패:', e)
  }
}

const handleViewInfo = async (cardId) => {
  try {
    const res = await getCardDetail(cardId)
    selectedCardDetail.value = res.data
  } catch (e) {
    console.error('카드 상세 정보 조회 실패:', e)
  }
}

const onClickGeneratePhysicalCard = async () => {
  try {
    await generatePhysicalCard()
    alert('✅ 실물 카드가 발급되었습니다.')
    await fetchCards()
  } catch (e) {
    console.error('카드 발급 실패:', e)
    alert('❌ 카드 발급에 실패했습니다: ' + (e.response?.data?.message || e.message))
  }
}

const onChangePin = () => {
  console.log('비밀번호 변경 이동')
}

onMounted(() => {
  fetchCards()
})
</script>
