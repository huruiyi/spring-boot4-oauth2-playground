const AUTH_SERVER = 'http://localhost:9000'
const RESOURCE_SERVER = 'http://localhost:9001'
const CLIENT_ID = 'spa-client-vue3'
const REDIRECT_URI = `${window.location.origin}/callback`
const SCOPES = 'openid profile read write'

function generateRandomString(length) {
  const array = new Uint8Array(length)
  crypto.getRandomValues(array)
  return Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('')
}

async function sha256(plain) {
  const encoder = new TextEncoder()
  const data = encoder.encode(plain)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return base64urlencode(digest)
}

function base64urlencode(buffer) {
  const bytes = new Uint8Array(buffer)
  let str = ''
  bytes.forEach((b) => (str += String.fromCharCode(b)))
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64urldecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/')
  while (str.length % 4) str += '='
  const binary = atob(str)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return new TextDecoder('utf-8').decode(bytes)
}

function parseJwt(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    return JSON.parse(base64urldecode(parts[1]))
  } catch {
    return null
  }
}

async function startAuthorization() {
  const codeVerifier = generateRandomString(32)
  const codeChallenge = await sha256(codeVerifier)
  sessionStorage.setItem('pkce_code_verifier', codeVerifier)

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: SCOPES,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256'
  })

  window.location.href = `${AUTH_SERVER}/oauth2/authorize?${params.toString()}`
}

async function exchangeCode(code) {
  const codeVerifier = sessionStorage.getItem('pkce_code_verifier')
  if (!codeVerifier) {
    throw new Error('未找到 code_verifier，请重新登录')
  }
  sessionStorage.removeItem('pkce_code_verifier')

  const params = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code_verifier: codeVerifier
  })

  const response = await fetch(`${AUTH_SERVER}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString()
  })

  if (!response.ok) {
    const error = await response.json()
    throw new Error(error.error_description || error.error || '令牌交换失败')
  }

  const tokenResponse = await response.json()
  sessionStorage.setItem('access_token', tokenResponse.access_token)
  if (tokenResponse.refresh_token) {
    sessionStorage.setItem('refresh_token', tokenResponse.refresh_token)
  }
  if (tokenResponse.id_token) {
    sessionStorage.setItem('id_token', tokenResponse.id_token)
  }
  sessionStorage.setItem('token_expires_at', String(Date.now() + tokenResponse.expires_in * 1000))

  return tokenResponse
}

function getAccessToken() {
  return sessionStorage.getItem('access_token')
}

function isAuthenticated() {
  const token = getAccessToken()
  if (!token) return false
  const expiresAt = sessionStorage.getItem('token_expires_at')
  if (expiresAt && Date.now() > Number(expiresAt)) {
    clearSession()
    return false
  }
  return true
}

function logout() {
  const idToken = sessionStorage.getItem('id_token')
  clearSession()

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: `${window.location.origin}/`
  })
  if (idToken) {
    params.set('id_token_hint', idToken)
  }

  window.location.href = `${AUTH_SERVER}/connect/logout?${params.toString()}`
}

async function callResourceServer(endpoint) {
  if (!isAuthenticated()) {
    clearSession()
    throw new Error('Token 已过期，请重新登录')
  }

  const token = getAccessToken()
  const response = await fetch(`${RESOURCE_SERVER}${endpoint}`, {
    headers: { Authorization: `Bearer ${token}` }
  })

  if (response.status === 401) {
    clearSession()
    throw new Error('401: 令牌无效或已过期，请重新登录')
  }

  if (!response.ok) {
    throw new Error(`请求失败: ${response.status} ${response.statusText}`)
  }

  return response.json()
}

function clearSession() {
  sessionStorage.removeItem('access_token')
  sessionStorage.removeItem('refresh_token')
  sessionStorage.removeItem('id_token')
  sessionStorage.removeItem('token_expires_at')
  sessionStorage.removeItem('pkce_code_verifier')
}

function startSilentRefresh() {
  const codeVerifier = generateRandomString(32)
  const iframe = document.createElement('iframe')
  iframe.style.display = 'none'

  sha256(codeVerifier).then((codeChallenge) => {
    sessionStorage.setItem('pkce_code_verifier', codeVerifier)
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      scope: SCOPES,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
      prompt: 'none'
    })
    iframe.src = `${AUTH_SERVER}/oauth2/authorize?${params.toString()}`
  })

  document.body.appendChild(iframe)

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      document.body.removeChild(iframe)
      reject(new Error('静默刷新超时'))
    }, 10000)

    window.addEventListener('message', function handler(e) {
      if (e.data?.type !== 'oauth2-callback') return
      clearTimeout(timer)
      document.body.removeChild(iframe)
      window.removeEventListener('message', handler)
      if (e.data.code) {
        exchangeCode(e.data.code).then(resolve).catch(reject)
      } else {
        reject(new Error('静默刷新失败'))
      }
    })
  })
}

export default {
  startAuthorization,
  exchangeCode,
  getAccessToken,
  isAuthenticated,
  logout,
  parseJwt,
  callResourceServer,
  startSilentRefresh,
  clearSession,
  AUTH_SERVER,
  RESOURCE_SERVER
}
