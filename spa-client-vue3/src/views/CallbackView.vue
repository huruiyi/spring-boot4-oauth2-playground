<template>
  <div class="callback">
    <div class="spinner"></div>
    <p>正在处理授权回调...</p>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import oauth2 from '../utils/oauth2.js'

const router = useRouter()

if (oauth2.isAuthenticated()) {
  router.push('/profile')
} else {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const error = params.get('error')

  if (error || !code) {
    router.push('/')
  } else {
    oauth2.exchangeCode(code)
      .then(() => { router.push('/profile') })
      .catch(() => { router.push('/') })
  }
}
</script>

<style scoped>
.callback { display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 100vh; background: #f5f5f5; }
.spinner { border: 4px solid #e0e0e0; border-top: 4px solid #4f46e5; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
p { margin-top: 16px; color: #555; }
</style>
