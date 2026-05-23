<template>
  <div class="profile">
    <h1>测试页 <span class="badge">已认证</span></h1>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="card">
      <h2>Token 状态</h2>
      <div class="token-status">
        <div class="info-row">
          <span class="info-label">有效期</span>
          <span :class="tokenRemainingClass">{{ tokenRemaining }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">过期时间</span>
          <span>{{ tokenExpiry }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">自动续期</span>
          <span>{{ autoRefreshEnabled ? '已开启 (过期前60秒)' : '未开启' }}</span>
        </div>
      </div>
      <div class="actions">
        <button class="btn btn-primary" @click="silentRefresh" :disabled="refreshing">
          {{ refreshing ? '续期中...' : '手动续期 (Silent Refresh)' }}
        </button>
        <button class="btn" @click="toggleAutoRefresh">
          {{ autoRefreshEnabled ? '关闭自动续期' : '开启自动续期' }}
        </button>
      </div>
    </div>

    <div class="card">
      <h2>用户信息</h2>
      <div v-for="field in displayFields" :key="field.key" class="info-row">
        <span class="info-label">{{ field.key }}</span>
        <span>{{ field.value }}</span>
      </div>
    </div>

    <div class="card">
      <h2>Access Token</h2>
      <pre class="token-display">{{ accessToken }}</pre>
      <div class="actions">
        <button class="btn btn-success" @click="fetchResourceServer">调用 Resource Server</button>
        <button class="btn btn-danger" @click="logout">登出</button>
      </div>
      <p class="hint">
        SPA 公共客户端无 refresh_token，通过 Silent Refresh (prompt=none) 续期。
      </p>
    </div>

    <div class="card">
      <h2>Resource Server 响应</h2>
      <pre class="response-display">{{ resourceResponse }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import oauth2 from '../utils/oauth2.js'

const accessToken = ref(oauth2.getAccessToken() || '')
const claims = ref(oauth2.parseJwt(accessToken.value))
const error = ref('')
const resourceResponse = ref('点击上方按钮调用 API')
const refreshing = ref(false)
const autoRefreshEnabled = ref(true)
const tokenRemaining = ref('')
const tokenExpiry = ref('')
const tokenRemainingClass = ref('')

let timer = null

const displayFields = computed(() => {
  if (!claims.value) return []
  const fields = ['sub', 'preferred_username', 'nickname', 'email', 'phone', 'scope', 'roles', 'iss', 'exp', 'iat']
  return fields
    .filter((key) => claims.value[key] !== undefined)
    .map((key) => {
      let value = claims.value[key]
      if (key === 'exp' || key === 'iat') value = new Date(value * 1000).toLocaleString()
      if (Array.isArray(value)) value = value.join(', ')
      return { key, value }
    })
})

function updateTokenStatus() {
  const expiresAt = Number(sessionStorage.getItem('token_expires_at'))
  if (!expiresAt) {
    tokenRemaining.value = '未知'
    tokenExpiry.value = '未知'
    tokenRemainingClass.value = ''
    return
  }

  const now = Date.now()
  const remaining = expiresAt - now
  tokenExpiry.value = new Date(expiresAt).toLocaleString()

  if (remaining <= 0) {
    tokenRemaining.value = '已过期'
    tokenRemainingClass.value = 'text-danger'
  } else {
    const min = Math.floor(remaining / 60000)
    const sec = Math.floor((remaining % 60000) / 1000)
    tokenRemaining.value = `${min}分${sec}秒`
    tokenRemainingClass.value = remaining < 120000 ? 'text-warning' : 'text-ok'
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(() => {
    updateTokenStatus()
    const expiresAt = Number(sessionStorage.getItem('token_expires_at'))
    if (!expiresAt) return

    const remaining = expiresAt - Date.now()
    if (remaining < 60000 && remaining > 0) {
      silentRefresh()
    }
    if (remaining <= 0) {
      oauth2.clearSession()
      window.location.href = '/'
    }
  }, 5000)
}

function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function toggleAutoRefresh() {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  if (autoRefreshEnabled.value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

async function silentRefresh() {
  refreshing.value = true
  error.value = ''
  try {
    await oauth2.startSilentRefresh()
    accessToken.value = oauth2.getAccessToken() || ''
    claims.value = oauth2.parseJwt(accessToken.value)
    updateTokenStatus()
  } catch (e) {
    error.value = 'Silent Refresh 失败: ' + e.message
  } finally {
    refreshing.value = false
  }
}

function logout() {
  oauth2.logout()
}

async function fetchResourceServer() {
  resourceResponse.value = '请求中...'
  try {
    const [info, messages] = await Promise.all([
      oauth2.callResourceServer('/api/user/info'),
      oauth2.callResourceServer('/api/user/messages')
    ])
    resourceResponse.value = JSON.stringify({ userInfo: info, messages }, null, 2)
  } catch (e) {
    resourceResponse.value = '请求失败: ' + e.message
    if (e.message.includes('401') || e.message.includes('过期')) {
      error.value = e.message + '，请点击手动续期或重新登录'
    }
  }
}

onMounted(() => {
  updateTokenStatus()
  if (autoRefreshEnabled.value) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.profile { max-width: 800px; margin: 40px auto; padding: 0 20px; }
h1 { font-size: 24px; margin-bottom: 20px; }
h2 { font-size: 18px; margin: 0 0 12px; color: #555; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; background: #dcfce7; color: #16a34a; vertical-align: middle; }
.card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.hint { margin-top: 12px; color: #666; font-size: 12px; }
.info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee; }
.info-label { font-weight: 600; color: #555; }
.error { color: #dc2626; background: #fef2f2; padding: 12px; border-radius: 6px; margin-bottom: 16px; }
.btn { display: inline-block; padding: 10px 24px; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background: #4f46e5; }
.btn-primary:hover { background: #4338ca; }
.btn-success { background: #16a34a; }
.btn-success:hover { background: #15803d; }
.btn-danger { background: #dc2626; }
.btn-danger:hover { background: #b91c1c; }
.token-display { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 13px; max-height: 150px; overflow-y: auto; word-break: break-all; }
.response-display { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 13px; max-height: 300px; overflow-y: auto; }
.text-ok { color: #16a34a; font-weight: 600; }
.text-warning { color: #d97706; font-weight: 600; }
.text-danger { color: #dc2626; font-weight: 600; }
</style>
