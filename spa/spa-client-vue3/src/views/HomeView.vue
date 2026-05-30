<template>
  <div class="home">
    <h1>SPA Client Vue3 <span class="badge">PKCE</span></h1>

    <div class="card">
      <h2>OAuth2 授权码 + PKCE 流程</h2>
      <p class="desc">
        此 SPA 应用使用公共客户端（client_id: <code>spa-client-vue3</code>），无 client_secret。<br>
        通过 PKCE (Proof Key for Code Exchange) 保护授权码流程的安全性。
      </p>
      <button class="btn btn-primary" @click="login">登录</button>
    </div>

    <div class="card">
      <h2>PKCE 流程说明</h2>
      <ol class="steps">
        <li>客户端生成随机 <code>code_verifier</code></li>
        <li>计算 <code>code_challenge = SHA-256(code_verifier)</code></li>
        <li>授权请求携带 <code>code_challenge</code></li>
        <li>令牌请求携带原始 <code>code_verifier</code></li>
        <li>授权服务器验证哈希匹配，签发令牌</li>
      </ol>
    </div>
  </div>
</template>

<script setup>
import oauth2 from '../utils/oauth2.js'

function login() {
  oauth2.startAuthorization()
}
</script>

<style scoped>
.home { max-width: 800px; margin: 40px auto; padding: 0 20px; }
h1 { font-size: 24px; margin-bottom: 20px; }
h2 { font-size: 18px; margin: 0 0 12px; color: #555; }
.badge { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 12px; background: #e0e7ff; color: #4f46e5; vertical-align: middle; font-weight: 600; letter-spacing: .3px; }
.card { background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid #f1f5f9; }
.desc { margin: 0 0 12px; color: #666; line-height: 1.6; }
.steps { padding-left: 20px; line-height: 1.8; color: #555; }
.btn { display: inline-flex; align-items: center; padding: 7px 18px; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500; transition: all .15s; gap: 4px; }
.btn-primary { background: #4f46e5; }
.btn-primary:hover { background: #4338ca; }
</style>
