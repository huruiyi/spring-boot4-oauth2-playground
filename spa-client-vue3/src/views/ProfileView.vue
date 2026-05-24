<template>
  <div class="profile">
    <!-- 顶部标题栏 -->
    <div class="top-bar">
      <h1>测试页 <span class="badge">公共客户端 (PKCE)</span></h1>
      <button class="btn btn-ghost" @click="logout">登出</button>
    </div>

    <div class="hint-bar">优先使用 refresh_token 续期，无 refresh_token 时 fallback 到 Silent Refresh (iframe prompt=none)</div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>
    <div v-if="lastAction" class="alert alert-info">{{ lastAction }}</div>

    <!-- Access Token -->
    <div class="card">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">🎫</span> Access Token
        </div>
        <span :class="countdownClass">{{ countdownText }}</span>
      </div>
      <div class="info-table">
        <div class="info-row">
          <div class="info-label">Token Type</div>
          <div class="info-value"><span class="mono">Bearer</span></div>
        </div>
        <div class="info-row">
          <div class="info-label">Issued At</div>
          <div class="info-value mono">{{ issuedAt }}</div>
        </div>
        <div class="info-row">
          <div class="info-label">Expires At</div>
          <div class="info-value mono">{{ tokenExpiry }}</div>
        </div>
        <div class="info-row highlight">
          <div class="info-label">即将过期</div>
          <div class="info-value" :class="expiresSoonClass">{{ expiresSoonText }}</div>
        </div>
        <div class="info-row">
          <div class="info-label">Scopes</div>
          <div class="info-value scopes">
            <span v-for="s in store.scopes" :key="s" class="scope-tag">{{ s }}</span>
            <span v-if="store.scopes.length === 0" class="text-muted">-</span>
          </div>
        </div>
        <div class="info-row">
          <div class="info-label">Refresh Token</div>
          <div class="info-value">
            <span v-if="store.refreshToken" class="status-dot dot-green"></span>
            <span v-else class="status-dot dot-amber"></span>
            <span :class="store.refreshToken ? 'text-ok' : 'text-warn'">{{ store.refreshToken ? '有 (旋转)' : 'No' }}</span>
          </div>
        </div>
        <div class="info-row">
          <div class="info-label">自动续期</div>
          <div class="info-value">
            <span v-if="autoRefreshEnabled" class="status-dot dot-green"></span>
            <span v-else class="status-dot dot-gray"></span>
            <span :class="autoRefreshEnabled ? 'text-ok' : 'text-muted'">{{ autoRefreshEnabled ? '已开启' : '未开启' }}</span>
            <span class="text-muted meta">过期前60秒</span>
          </div>
        </div>
      </div>
      <div class="actions">
        <button class="btn btn-primary" @click="doRefresh" :disabled="refreshing">
          {{ refreshing ? '⏳ 续期中...' : '🔄 手动续期' }}
        </button>
        <button class="btn btn-outline" @click="toggleAutoRefresh">
          {{ autoRefreshEnabled ? '关闭自动续期' : '开启自动续期' }}
        </button>
      </div>
      <div class="code-block">
        <div class="code-header"><span>access_token</span></div>
        <pre class="code-body">{{ store.accessToken }}</pre>
      </div>
    </div>

    <!-- ID Token -->
    <div class="card">
      <div class="card-header">
        <div class="card-title"><span class="card-icon">📋</span> ID Token</div>
      </div>
      <div class="info-table">
        <div class="info-row">
          <div class="info-label">Issued At</div>
          <div class="info-value mono">{{ idTokenIssuedAt }}</div>
        </div>
        <div class="info-row">
          <div class="info-label">Expires At</div>
          <div class="info-value mono">{{ idTokenExpiresAt }}</div>
        </div>
      </div>
      <div class="section-label">Claims</div>
      <div class="info-table">
        <div class="info-row" v-for="field in displayFields" :key="field.key">
          <div class="info-label">{{ field.key }}</div>
          <div class="info-value" :class="isLongValue(field.value) ? 'mono-sm' : ''">{{ field.value }}</div>
        </div>
      </div>
    </div>

    <!-- API -->
    <div class="api-grid">
      <div class="card">
        <div class="card-header">
          <div class="card-title"><span class="card-icon">🌐</span> /api/user/info</div>
          <button class="api-btn" @click="callApi('/api/user/info')">请求</button>
        </div>
        <div class="code-block">
          <pre class="code-body resp">{{ userInfoResponse }}</pre>
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <div class="card-title"><span class="card-icon">💬</span> /api/user/messages</div>
          <button class="api-btn" @click="callApi('/api/user/messages')">请求</button>
        </div>
        <div class="code-block">
          <pre class="code-body resp">{{ messagesResponse }}</pre>
        </div>
      </div>
    </div>

    <!-- Token Introspection / Revocation -->
    <div class="card">
      <div class="card-header">
        <div class="card-title"><span class="card-icon">🔍</span> Token Introspection & Revocation</div>
      </div>
      <div class="hint-bar" style="margin-bottom:12px">Introspection 通过 /oauth2/introspect (oidc-client Basic Auth)；Revocation 通过自定义 /api/revoke 端点直接操作 OAuth2AuthorizationService 吊销 token。<br>注意：JWT 是无状态的，吊销不会立即使 resource-server 拒绝，需本地清除 token。</div>
      <div class="api-grid" style="grid-template-columns: 1fr 1fr">
        <div>
          <div class="section-label">Introspection</div>
          <div class="actions" style="margin-top:0;margin-bottom:8px">
            <button class="btn btn-outline btn-sm" @click="doIntrospect('access_token')">Introspect Access Token</button>
          </div>
          <div class="code-block">
            <div class="code-header"><span>/oauth2/introspect</span></div>
            <pre class="code-body resp">{{ introspectResult }}</pre>
          </div>
        </div>
        <div>
          <div class="section-label">Revocation</div>
          <div class="actions" style="margin-top:0;margin-bottom:8px">
            <button class="btn btn-outline btn-sm btn-warn" @click="doRevoke('access_token')">Revoke Access Token</button>
          </div>
          <div class="code-block">
            <div class="code-header"><span>/api/revoke</span></div>
            <pre class="code-body resp">{{ revokeResult }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useOAuth2Store } from '../stores/oauth2.js'
import oauth2 from '../utils/oauth2.js'

const store = useOAuth2Store()

const error = ref('')
const refreshing = ref(false)
const autoRefreshEnabled = ref(true)

const countdownText = ref('')
const countdownClass = ref('')
const issuedAt = ref('-')
const tokenExpiry = ref('-')
const expiresSoonText = ref('-')
const expiresSoonClass = ref('')
const idTokenIssuedAt = ref('-')
const idTokenExpiresAt = ref('-')
const userInfoResponse = ref('点击请求按钮')
const messagesResponse = ref('点击请求按钮')
const lastAction = ref('')
const introspectResult = ref('点击 Introspect 按钮')
const revokeResult = ref('点击 Revoke 按钮')

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

function isLongValue(v) {
  return typeof v === 'string' && v.length > 40
}

const displayFields = computed(() => {
  const c = store.claims
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
  const c = store.claims || {}
  const expMs = store.expiresAtMs || 0
  const now = Date.now()

  if (c.iat) issuedAt.value = fmtDate(c.iat * 1000)
  if (expMs > 0) tokenExpiry.value = fmtDate(expMs)
  if (c.iat) idTokenIssuedAt.value = fmtDate(c.iat * 1000)
  if (c.exp) idTokenExpiresAt.value = fmtDate(c.exp * 1000)

  if (!expMs || expMs <= 0) {
    countdownText.value = ''
    countdownClass.value = ''
    expiresSoonText.value = '-'
    expiresSoonClass.value = ''
    return
  }

  const remaining = expMs - now

  if (remaining <= 0) {
    countdownText.value = '已过期'
    countdownClass.value = 'cd-expired'
    expiresSoonText.value = '已过期'
    expiresSoonClass.value = 'text-danger'
    return
  }

  countdownText.value = fmtRemaining(remaining)
  countdownClass.value = remaining < 60000 ? 'cd-warn' : 'cd-ok'
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
  countdownTimer = setInterval(updateTokenStatus, 1000)
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

function startAutoRefresh() {
  stopAutoRefresh()
  let lastRefreshTime = 0
  refreshTimer = setInterval(() => {
    const rem = store.expiresAtMs ? store.expiresAtMs - Date.now() : 0
    const now = Date.now()
    if (rem < 60000 && rem > 0 && !refreshing.value && (now - lastRefreshTime > 5000)) {
      lastRefreshTime = now
      lastAction.value = `[${new Date().toLocaleTimeString()}] 自动续期触发, remaining=${Math.round(rem / 1000)}s`
      doRefresh()
    }
    if (rem <= 0) {
      store.clear()
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

async function doRefresh() {
  refreshing.value = true
  error.value = ''
  const hasRt = !!store.refreshToken
  lastAction.value = `[${new Date().toLocaleTimeString()}] 续期开始, refreshToken=${hasRt}`
  try {
    if (hasRt) {
      await oauth2.refreshToken()
      lastAction.value += ' → refreshToken成功'
    } else {
      await oauth2.startSilentRefresh()
      lastAction.value += ' → silentRefresh成功'
    }
    updateTokenStatus()
  } catch (e) {
    error.value = '续期失败: ' + e.message
    lastAction.value += ' → 失败: ' + e.message
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

async function doIntrospect(tokenTypeHint) {
  const token = tokenTypeHint === 'refresh_token' ? store.refreshToken : store.accessToken
  if (!token) {
    introspectResult.value = `无 ${tokenTypeHint}`
    return
  }
  introspectResult.value = '请求中...'
  try {
    const data = await oauth2.introspectToken(token, tokenTypeHint)
    introspectResult.value = JSON.stringify(data, null, 2)
  } catch (e) {
    introspectResult.value = '失败: ' + e.message
  }
}

async function doRevoke(tokenTypeHint) {
  const token = tokenTypeHint === 'refresh_token' ? store.refreshToken : store.accessToken
  if (!token) {
    revokeResult.value = `无 ${tokenTypeHint}`
    return
  }
  revokeResult.value = '请求中...'
  try {
    await oauth2.revokeToken(token, tokenTypeHint)
    revokeResult.value = `✓ ${tokenTypeHint} 已吊销\n(HTTP 200, 无响应体)\n\nJWT 是无状态的，吊销不会立即使 resource-server 拒绝请求。\n已清除本地 token，请重新登录。`
    store.clear()
    updateTokenStatus()
  } catch (e) {
    revokeResult.value = '失败: ' + e.message
  }
}

onMounted(() => {
  store.restore()
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
.profile { max-width: 920px; margin: 32px auto; padding: 0 20px; }

.top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
h1 { font-size: 22px; margin: 0; color: #1e293b; }
.badge { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 12px; background: #e0e7ff; color: #4f46e5; vertical-align: middle; font-weight: 600; letter-spacing: .3px; }

.hint-bar { color: #64748b; background: #f8fafc; padding: 10px 16px; border-radius: 8px; margin-bottom: 16px; font-size: 12px; border: 1px solid #e2e8f0; line-height: 1.5; }

.alert { padding: 10px 16px; border-radius: 8px; margin-bottom: 12px; font-size: 13px; line-height: 1.5; }
.alert-error { color: #dc2626; background: #fef2f2; border: 1px solid #fecaca; }
.alert-info { color: #4f46e5; background: #eef2ff; border: 1px solid #c7d2fe; font-size: 12px; font-family: ui-monospace, monospace; }

.card { background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid #f1f5f9; }

.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 2px solid #f1f5f9; }
.card-title { font-size: 15px; font-weight: 600; color: #334155; display: flex; align-items: center; gap: 6px; }
.card-icon { font-size: 17px; }

.info-table { margin-bottom: 4px; }
.info-row { display: flex; align-items: center; padding: 0; border-radius: 6px; margin-bottom: 2px; font-size: 13px; }
.info-row.highlight { background: #fffbeb; }
.info-label { font-weight: 600; color: #94a3b8; min-width: 120px; flex-shrink: 0; padding: 8px 12px; font-size: 12px; text-transform: uppercase; letter-spacing: .5px; }
.info-value { color: #1e293b; padding: 8px 12px; flex: 1; display: flex; align-items: center; justify-content: flex-end; gap: 6px; flex-wrap: wrap; font-weight: 500; }
.scopes { justify-content: flex-end; }

.mono { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 12.5px; font-weight: 400; }
.mono-sm { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 11.5px; font-weight: 400; word-break: break-all; }

.scope-tag { display: inline-block; padding: 2px 10px; background: #eef2ff; color: #4f46e5; border-radius: 10px; font-size: 11px; font-weight: 600; border: 1px solid #c7d2fe; }

.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.dot-green { background: #22c55e; box-shadow: 0 0 4px rgba(34,197,94,.4); }
.dot-amber { background: #f59e0b; box-shadow: 0 0 4px rgba(245,158,11,.4); }
.dot-gray { background: #cbd5e1; }

.meta { margin-left: 6px; font-weight: 400; font-size: 12px; }

.text-warn { color: #d97706; font-weight: 600; }
.text-danger { color: #dc2626; font-weight: 600; }
.text-ok { color: #16a34a; font-weight: 600; }
.text-muted { color: #94a3b8; }

.cd-ok { color: #16a34a; font-weight: 700; font-size: 14px; }
.cd-warn { color: #d97706; font-weight: 700; font-size: 14px; }
.cd-expired { color: #dc2626; font-weight: 700; font-size: 14px; }

.section-label { color: #64748b; font-size: 12px; margin: 16px 0 8px; font-weight: 600; text-transform: uppercase; letter-spacing: .5px; }

.actions { margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap; }

.btn { display: inline-flex; align-items: center; padding: 7px 18px; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500; transition: all .15s; gap: 4px; }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn-primary { background: #4f46e5; }
.btn-primary:hover:not(:disabled) { background: #4338ca; }
.btn-outline { background: transparent; color: #64748b; border: 1px solid #e2e8f0; }
.btn-outline:hover:not(:disabled) { background: #f8fafc; color: #334155; }
.btn-ghost { background: transparent; color: #94a3b8; border: 1px solid #e2e8f0; }
.btn-ghost:hover { background: #fef2f2; color: #dc2626; border-color: #fecaca; }
.btn-sm { padding: 5px 12px; font-size: 12px; }
.btn-warn { color: #d97706; border-color: #fed7aa; }
.btn-warn:hover:not(:disabled) { background: #fffbeb; color: #b45309; border-color: #fdba74; }

.code-block { border-radius: 8px; overflow: hidden; border: 1px solid #dee2e6; margin-top: 12px; }
.code-header { background: #f1f3f5; padding: 6px 14px; font-size: 11px; color: #495057; font-weight: 500; text-transform: uppercase; letter-spacing: .5px; }
.code-body { background: #f8f9fa; color: #212529; padding: 12px 14px; margin: 0; overflow: auto; font-size: 12px; max-height: 200px; word-break: break-all; white-space: pre-wrap; line-height: 1.6; font-family: ui-monospace, SFMono-Regular, monospace; }
.code-body.resp { max-height: none; color: #212529; white-space: pre-wrap; word-break: break-word; overflow: hidden; }

.api-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.api-btn { padding: 5px 14px; background: #4f46e5; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 12px; font-weight: 500; transition: all .15s; }
.api-btn:hover { background: #4338ca; }
</style>
