<template>
  <div class="home">
    <h1>SPA Client Vue3 <span class="badge">PKCE</span></h1>

    <div v-if="error" class="error">{{ error }}</div>

    <div v-if="!authenticated" class="card">
      <h2>OAuth2 授权码 + PKCE 流程</h2>
      <p class="desc">
        此 SPA 应用使用公共客户端（client_id: <code>spa-client-vue3</code>），无 client_secret。<br>
        通过 PKCE (Proof Key for Code Exchange) 保护授权码流程的安全性。
      </p>
      <button class="btn btn-primary" @click="login">登录</button>
    </div>

    <div v-if="!authenticated" class="card">
      <h2>PKCE 流程说明</h2>
      <ol class="steps">
        <li>客户端生成随机 <code>code_verifier</code></li>
        <li>计算 <code>code_challenge = SHA-256(code_verifier)</code></li>
        <li>授权请求携带 <code>code_challenge</code></li>
        <li>令牌请求携带原始 <code>code_verifier</code></li>
        <li>授权服务器验证哈希匹配，签发令牌</li>
      </ol>
    </div>

    <template v-if="authenticated">
      <div class="card">
        <h2>用户信息</h2>
        <div class="info-rows">
          <div v-for="field in displayFields" :key="field.key" class="info-row">
            <span class="info-label">{{ field.key }}</span>
            <span>{{ field.value }}</span>
          </div>
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
          <strong>注意</strong>：SPA 作为公共客户端不持有刷新令牌，Token 过期后请重新登录。
        </p>
      </div>

      <div class="card">
        <h2>Resource Server 响应</h2>
        <pre class="response-display">{{ resourceResponse }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import oauth2 from '../utils/oauth2.js'

const authenticated = ref(false)
const accessToken = ref('')
const claims = ref(null)
const error = ref('')
const resourceResponse = ref('点击上方按钮调用 API')

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

function login() {
  oauth2.startAuthorization()
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
  }
}

onMounted(() => {
  if (oauth2.isAuthenticated()) {
    authenticated.value = true
    accessToken.value = oauth2.getAccessToken()
    claims.value = oauth2.parseJwt(accessToken.value)
  }
})
</script>

<style scoped>
.home { max-width: 800px; margin: 40px auto; padding: 0 20px; }
h1 { font-size: 24px; margin-bottom: 20px; }
h2 { font-size: 18px; margin: 0 0 12px; color: #555; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; background: #e0e7ff; color: #4f46e5; vertical-align: middle; }
.card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.desc { margin: 0 0 12px; color: #666; line-height: 1.6; }
.steps { padding-left: 20px; line-height: 1.8; color: #555; }
.btn { display: inline-block; padding: 10px 24px; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
.btn-primary { background: #4f46e5; }
.btn-primary:hover { background: #4338ca; }
.btn-success { background: #16a34a; }
.btn-success:hover { background: #15803d; }
.btn-danger { background: #dc2626; }
.btn-danger:hover { background: #b91c1c; }
.actions { margin-top: 12px; display: flex; gap: 8px; }
.hint { margin-top: 12px; color: #666; font-size: 12px; }
.info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee; }
.info-label { font-weight: 600; color: #555; }
.error { color: #dc2626; background: #fef2f2; padding: 12px; border-radius: 6px; margin-bottom: 16px; }
.token-display { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 13px; max-height: 150px; overflow-y: auto; word-break: break-all; }
.response-display { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 13px; max-height: 300px; overflow-y: auto; }
</style>
