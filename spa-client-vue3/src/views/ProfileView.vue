<template>
  <div class="profile">
    <h1>测试页 <span class="badge">公共客户端 (PKCE)</span></h1>

    <div v-if="error" class="error-box">{{ error }}</div>

    <!-- Access Token -->
    <div class="card">
      <h2>
        <span class="card-icon">🎫</span> Access Token
        <span :class="countdownClass">{{ countdownText }}</span>
      </h2>
      <div class="info-row">
        <span class="info-label">Token Type</span>
        <span class="info-value">Bearer</span>
      </div>
      <div class="info-row">
        <span class="info-label">Issued At</span>
        <span class="info-value">{{ issuedAt }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">Expires At</span>
        <span class="info-value">{{ expiresAt }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">即将过期</span>
        <span class="info-value" :class="expiresSoonClass">{{ expiresSoonText }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">Scopes</span>
        <span class="info-value scopes">
          <span v-for="s in scopes" :key="s" class="tag">{{ s }}</span>
          <span v-if="scopes.length === 0">-</span>
        </span>
      </div>
      <div class="info-row">
        <span class="info-label">Refresh Token</span>
        <span class="info-value text-warn">No (公共客户端)</span>
      </div>
      <div class="info-row">
        <span class="info-label">自动续期</span>
        <span class="info-value">{{ autoRefreshEnabled ? '已开启 (过期前60秒 Silent Refresh)' : '未开启' }}</span>
      </div>
      <div class="actions">
        <button class="btn btn-primary" @click="silentRefresh" :disabled="refreshing">
          {{ refreshing ? '续期中...' : '手动续期 (Silent Refresh)' }}
        </button>
        <button class="btn btn-outline" @click="toggleAutoRefresh">
          {{ autoRefreshEnabled ? '关闭自动续期' : '开启自动续期' }}
        </button>
      </div>
      <pre class="token-display">{{ accessToken }}</pre>
    </div>

    <!-- ID Token -->
    <div class="card">
      <h2><span class="card-icon">📋</span> ID Token</h2>
      <div class="info-row">
        <span class="info-label">Issued At</span>
        <span class="info-value">{{ idTokenIssuedAt }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">Expires At</span>
        <span class="info-value">{{ idTokenExpiresAt }}</span>
      </div>
      <p class="sub-label">Claims</p>
      <div v-for="field in displayFields" :key="field.key" class="info-row">
        <span class="info-label">{{ field.key }}</span>
        <span class="info-value">{{ field.value }}</span>
      </div>
    </div>

    <!-- API 两列布局 -->
    <div class="api-grid">
      <div class="card">
        <h2>
          <span class="card-icon">🌐</span> /api/user/info
          <button class="api-btn" @click="callApi('/api/user/info')">请求</button>
        </h2>
        <pre class="response-display">{{ userInfoResponse }}</pre>
      </div>
      <div class="card">
        <h2>
          <span class="card-icon">💬</span> /api/user/messages
          <button class="api-btn" @click="callApi('/api/user/messages')">请求</button>
        </h2>
        <pre class="response-display">{{ messagesResponse }}</pre>
      </div>
    </div>

    <div class="card card-footer">
      <div class="actions">
        <button class="btn btn-danger" @click="logout">登出</button>
      </div>
      <p class="hint">
        SPA 公共客户端 (ClientAuthenticationMethod.NONE) 无 refresh_token，通过 Silent Refresh (prompt=none) 续期。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import oauth2 from '../utils/oauth2.js'

const accessToken = ref(oauth2.getAccessToken() || '')
const claims = ref(oauth2.parseJwt(accessToken.value) || {})
const error = ref('')
const refreshing = ref(false)
const autoRefreshEnabled = ref(true)

const countdownText = ref('')
const countdownClass = ref('')
const issuedAt = ref('-')
const expiresAt = ref('-')
const expiresSoonText = ref('-')
const expiresSoonClass = ref('')
const idTokenIssuedAt = ref('-')
const idTokenExpiresAt = ref('-')
const scopes = ref([])
const userInfoResponse = ref('点击请求按钮')
const messagesResponse = ref('点击请求按钮')

let countdownTimer = null
let refreshTimer = null

function fmtDate(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function fmtRemaining(ms) {
  const min = Math.floor(ms / 60000)
  const sec = Math.floor((ms % 60000) / 1000)
  if (min === 0) return `${sec}秒`
  return `${min}分${sec}秒`
}

const displayFields = computed(() => {
  const c = claims.value
  if (!c) return []
  const fields = ['sub', 'preferred_username', 'nickname', 'email', 'phone', 'scp', 'scope', 'roles', 'iss', 'aud', 'azp', 'jti', 'sid']
  return fields
    .filter((key) => c[key] !== undefined)
    .map((key) => {
      let value = c[key]
      if (key === 'exp' || key === 'iat' || key === 'auth_time') value = fmtDate(value * 1000)
      if (Array.isArray(value)) value = value.join(', ')
      return { key, value }
    })
})

function updateTokenStatus() {
  const c = claims.value || {}

  // 从 sessionStorage 获取 token_expires_at，如果没有则从 JWT exp 字段推算
  let expiresAtMs = Number(sessionStorage.getItem('token_expires_at'))
  if ((!expiresAtMs || isNaN(expiresAtMs)) && c.exp) {
    expiresAtMs = c.exp * 1000
  }

  const now = Date.now()

  // Issued At
  if (c.iat) {
    issuedAt.value = fmtDate(c.iat * 1000)
  }

  // Expires At
  if (expiresAtMs > 0) {
    expiresAt.value = fmtDate(expiresAtMs)
  }

  // Scopes (Spring Authorization Server uses 'scp' for access_token, 'scope' for id_token)
  const scopeStr = c.scp || c.scope || ''
  scopes.value = scopeStr ? (typeof scopeStr === 'string' ? scopeStr.split(' ').filter(Boolean) : scopeStr) : []

  // ID Token times
  if (c.iat) idTokenIssuedAt.value = fmtDate(c.iat * 1000)
  if (c.exp) idTokenExpiresAt.value = fmtDate(c.exp * 1000)

  // Countdown + 即将过期
  if (!expiresAtMs || isNaN(expiresAtMs) || expiresAtMs <= 0) {
    countdownText.value = ''
    countdownClass.value = ''
    expiresSoonText.value = '-'
    expiresSoonClass.value = ''
    return
  }

  const remaining = expiresAtMs - now

  if (remaining <= 0) {
    countdownText.value = '已过期'
    countdownClass.value = 'cd-expired'
    expiresSoonText.value = '已过期'
    expiresSoonClass.value = 'text-danger'
    return
  }

  // 标题旁倒计时
  countdownText.value = `(${fmtRemaining(remaining)})`
  countdownClass.value = remaining < 60000 ? 'cd-warn' : 'cd-ok'

  // 即将过期行
  expiresSoonText.value = `${fmtRemaining(remaining)} 后过期`
  if (remaining < 60000) {
    expiresSoonClass.value = 'text-danger'
  } else if (remaining < 120000) {
    expiresSoonClass.value = 'text-warn'
  } else {
    expiresSoonClass.value = 'text-ok'
  }
}

function startCountdown() {
  stopCountdown()
  countdownTimer = setInterval(() => {
    updateTokenStatus()
  }, 1000)
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    let expiresAtMs = Number(sessionStorage.getItem('token_expires_at'))
    if (!expiresAtMs || isNaN(expiresAtMs)) {
      const c = claims.value || {}
      if (c.exp) expiresAtMs = c.exp * 1000
    }
    if (!expiresAtMs) return
    const remaining = expiresAtMs - Date.now()
    if (remaining < 60000 && remaining > 0 && !refreshing.value) {
      silentRefresh()
    }
    if (remaining <= 0) {
      oauth2.clearSession()
      window.location.href = '/'
    }
  }, 1000)
}

function stopAutoRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}

function toggleAutoRefresh() {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  if (autoRefreshEnabled.value) startAutoRefresh()
  else stopAutoRefresh()
}

async function silentRefresh() {
  refreshing.value = true
  error.value = ''
  try {
    await oauth2.startSilentRefresh()
    accessToken.value = oauth2.getAccessToken() || ''
    claims.value = oauth2.parseJwt(accessToken.value) || {}
    updateTokenStatus()
  } catch (e) {
    error.value = 'Silent Refresh 失败: ' + e.message
  } finally {
    refreshing.value = false
  }
}

function logout() { oauth2.logout() }

async function callApi(endpoint) {
  if (endpoint === '/api/user/info') userInfoResponse.value = '请求中...'
  else messagesResponse.value = '请求中...'
  try {
    const data = await oauth2.callResourceServer(endpoint)
    if (endpoint === '/api/user/info') userInfoResponse.value = JSON.stringify(data, null, 2)
    else messagesResponse.value = JSON.stringify(data, null, 2)
  } catch (e) {
    if (endpoint === '/api/user/info') userInfoResponse.value = '请求失败: ' + e.message
    else messagesResponse.value = '请求失败: ' + e.message
    if (e.message.includes('401') || e.message.includes('过期')) {
      error.value = e.message + '，请点击手动续期或重新登录'
    }
  }
}

onMounted(() => {
  updateTokenStatus()
  startCountdown()
  if (autoRefreshEnabled.value) startAutoRefresh()
})

onUnmounted(() => {
  stopCountdown()
  stopAutoRefresh()
})
</script>

<style scoped>
.profile {
  max-width: 920px;
  margin: 32px auto;
  padding: 0 20px;
}
h1 {
  font-size: 22px;
  margin-bottom: 20px;
  color: #1e293b;
}
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 12px;
  background: #e0e7ff;
  color: #4f46e5;
  vertical-align: middle;
  font-weight: 500;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04);
}
.card-footer {
  text-align: center;
}
h2 {
  font-size: 16px;
  margin: 0 0 16px;
  color: #334155;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 12px;
}
.card-icon {
  font-size: 18px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f8fafc;
  font-size: 13px;
}
.info-label {
  font-weight: 500;
  color: #94a3b8;
  min-width: 120px;
  flex-shrink: 0;
}
.info-value {
  color: #334155;
  text-align: right;
  flex: 1;
}
.scopes {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.tag {
  display: inline-block;
  padding: 2px 10px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}
.text-warn { color: #d97706; font-weight: 600; }
.text-danger { color: #dc2626; font-weight: 600; }
.text-ok { color: #16a34a; font-weight: 500; }

.cd-ok { color: #16a34a; font-weight: 600; font-size: 13px; }
.cd-warn { color: #d97706; font-weight: 600; font-size: 13px; }
.cd-expired { color: #dc2626; font-weight: 600; font-size: 13px; }

.error-box {
  color: #dc2626;
  background: #fef2f2;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid #fecaca;
  font-size: 13px;
}
.sub-label {
  color: #94a3b8;
  font-size: 13px;
  margin: 16px 0 8px;
  font-weight: 500;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  margin-top: 12px;
  color: #94a3b8;
  font-size: 12px;
}
.btn {
  display: inline-block;
  padding: 8px 20px;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.15s;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background: #4f46e5; }
.btn-primary:hover:not(:disabled) { background: #4338ca; }
.btn-outline {
  background: transparent;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.btn-outline:hover:not(:disabled) { background: #f8fafc; color: #334155; }
.btn-danger { background: #dc2626; }
.btn-danger:hover:not(:disabled) { background: #b91c1c; }
.token-display {
  background: #1e293b;
  color: #e2e8f0;
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
  max-height: 120px;
  overflow-y: auto;
  word-break: break-all;
  margin-top: 12px;
  line-height: 1.6;
}
.response-display {
  background: #1e293b;
  color: #e2e8f0;
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
  max-height: 250px;
  overflow-y: auto;
  line-height: 1.6;
}
.api-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.api-btn {
  padding: 4px 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  margin-left: auto;
}
.api-btn:hover { opacity: 0.9; }
</style>
