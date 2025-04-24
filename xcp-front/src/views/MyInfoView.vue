<template>
  <div class="p-4">
    <h2 class="text-xl font-bold mb-6 text-center">내 정보 수정</h2>

    <!-- 로딩 중 -->
    <div v-if="loading" class="text-center text-gray-400">
      사용자 정보를 불러오는 중...
    </div>

    <!-- 데이터가 준비되면 폼 렌더링 -->
    <div v-else-if="user" class="bg-white rounded-xl shadow p-4 space-y-4">
      <!-- 수정 불가 정보 -->
      <InfoItem label="이메일"       :value="user.userEmail" />
      <InfoItem label="전화번호"     :value="user.userPhoneNumber" />
      <InfoItem label="이름"         :value="user.userName" />
      <InfoItem label="가입일"       :value="formatDate(user.userJoinDate)" />

      <!-- 수정 폼 -->
      <form @submit.prevent="handleSubmit" class="space-y-4">
        <!-- 닉네임 -->
        <div>
          <label class="form-label">닉네임</label>
          <input
              v-model="form.userNickName"
              type="text"
              class="form-control"
              required
          />
        </div>

        <!-- 나이 -->
        <div>
          <label class="form-label">나이</label>
          <input
              v-model.number="form.userAge"
              type="number"
              class="form-control"
              min="0"
              required
          />
        </div>

        <!-- 성별 -->
        <div>
          <label class="form-label">성별</label>
          <select v-model="form.userSex" class="form-select" required>
            <option value="MALE">남성</option>
            <option value="FEMALE">여성</option>
          </select>
        </div>

        <!-- 버튼 그룹 -->
        <div class="d-flex justify-content-center gap-2 mt-6">
          <button type="submit" class="btn btn-primary">
            수정하기
          </button>
          <button
              type="button"
              class="btn btn-outline-danger"
              @click="handleDeleteAccount"
          >
            탈퇴하기
          </button>
        </div>

        <!-- 일반 오류 메시지 -->
        <div v-if="errorMessage" class="text-red-500 text-sm mt-2">
          {{ errorMessage }}
        </div>
        <!-- 필드별 검증 오류 메시지 -->
        <div
            v-for="(msg, field) in validationErrors"
            :key="field"
            class="text-red-500 text-xs"
        >
          • {{ msg }}
        </div>
      </form>
    </div>

    <!-- 데이터 불러오기 실패 -->
    <div v-else class="text-center text-red-500">
      사용자 정보를 불러올 수 없습니다.
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import InfoItem from '@/components/common/InfoItem.vue'
import { getMyInfo, updateMyInfo, deleteMyAccount } from '@/api/userApi'

const router = useRouter()
const user = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const validationErrors = ref({})

// 폼 필드명 userNickName (GET 응답 키 이름) 으로 통일
const form = reactive({
  userNickName: '',
  userAge: null,
  userSex: 'MALE',
})

const formatDate = (dateStr) => dateStr?.slice(0, 10)

onMounted(async () => {
  try {
    const { data } = await getMyInfo()
    user.value = data
    form.userNickName = data.userNickName
    form.userAge      = data.userAge
    form.userSex      = data.userSex
  } catch (e) {
    console.error('내 정보 조회 실패', e)
    alert('사용자 정보를 불러오는 중 오류가 발생했습니다.')
    router.back()
  } finally {
    loading.value = false
  }
})

const handleSubmit = async () => {
  errorMessage.value = ''
  validationErrors.value = {}

  try {
    await updateMyInfo({
      userNickname: form.userNickName,
      userAge:      form.userAge,
      userSex:      form.userSex,
    })
    alert('내 정보가 성공적으로 수정되었습니다.')
    router.push({ name: 'MyPage' })
  } catch (e) {
    const data = typeof e.response.data === 'string'
        ? JSON.parse(e.response.data)
        : e.response.data

    console.error('▶ 전체 오류 응답:', data)
    console.error('▶ message:', data.message)
    console.error('▶ validation:', data.validation)

    errorMessage.value     = data.message || '알 수 없는 오류가 발생했습니다.'
    validationErrors.value = data.validation || {}
  }
}

const handleDeleteAccount = async () => {
  const message = [
    '회원탈퇴 요청 시 계정이 비활성화됩니다.',
    '30일 이후에 완전히 삭제됩니다.'
  ].join('\n\n')
  const confirmed = window.confirm(message)
  if (!confirmed) return

  try {
    await deleteMyAccount()
    alert('탈퇴 요청이 접수되었습니다.')
    router.push('/user-login')
  } catch (e) {
    console.error('회원 탈퇴 오류', e)
    alert('탈퇴 중 오류가 발생했습니다.')
  }
}
</script>
