<template>
  <div class="modal-mask" v-if="show">
    <div class="modal-wrapper">
      <div class="modal-container">
        <h3 class="modal-title">🔐 지갑 비밀번호 확인</h3>
        <form @submit.prevent="handleConfirm">
          <input
              v-model="password"
              type="password"
              class="form-control mt-3"
              placeholder="비밀번호 입력"
              required
          />
          <div class="mt-4 flex justify-end gap-2">
            <button type="button" class="btn btn-secondary" @click="handleCancel">취소</button>
            <button type="submit" class="btn btn-primary">확인</button>
          </div>
        </form>
        <p v-if="error" class="text-danger mt-2 text-sm">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'
import { defineEmits, defineProps } from 'vue'

const props = defineProps({
  show: Boolean,
})

const emit = defineEmits(['close', 'success'])

const password = ref('')
const error = ref('')

const handleConfirm = async () => {
  try {
    const res = await axios.post('http://localhost:8080/api/v1/wallet/verify-password', {
      password: password.value
    })
    if (res.data.success) {
      emit('success')
      reset()
    } else {
      error.value = '비밀번호가 일치하지 않습니다.'
    }
  } catch (e) {
    error.value = '확인 실패: ' + (e.response?.data?.message || '서버 오류')
  }
}

const handleCancel = () => {
  emit('close')
  reset()
}

const reset = () => {
  password.value = ''
  error.value = ''
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  z-index: 9999;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background-color: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-wrapper {
  width: 100%;
  max-width: 400px;
  background: white;
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
}

.modal-title {
  font-weight: bold;
  font-size: 1.25rem;
  text-align: center;
}
</style>
