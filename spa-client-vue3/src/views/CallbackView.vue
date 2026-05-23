<template>
  <div class="callback">
    <div class="spinner"></div>
    <p :class="statusClass">{{ status }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import oauth2 from '../utils/oauth2.js'

const router = useRouter()
const status = ref('正在交换授权码获取令牌...')
const statusClass = ref('')

onMounted(async () => {
  try {
    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const error = params.get('error')
    const errorDesc = params.get('error_description')

    if (error) {
      status.value = '授权失败: ' + (errorDesc || error)
      statusClass.value = 'error'
      setTimeout(() => router.push('/'), 3000)
      return
    }

    if (!code) {
      status.value = '未收到授权码'
      statusClass.value = 'error'
      setTimeout(() => router.push('/'), 3000)
      return
    }

    await oauth2.exchangeCode(code)
    router.push('/')
  } catch (e) {
    status.value = '令牌交换失败: ' + e.message
    statusClass.value = 'error'
  }
})
</script>

<style scoped>
.callback { display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 100vh; background: #f5f5f5; }
.spinner { border: 4px solid #e0e0e0; border-top: 4px solid #4f46e5; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
p { margin-top: 16px; color: #555; }
.error { color: #dc2626; }
</style>
