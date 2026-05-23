import { generateRandomString, sha256, parseJwt } from './crypto.js'
import { authServerClient, resourceServerClient, AUTH_SERVER, RESOURCE_SERVER } from './http.js'
import { useOAuth2Store } from '../stores/oauth2.js'

const CLIENT_ID = 'spa-client-vue3'
const REDIRECT_URI = `${window.location.origin}/callback`
const SCOPES = 'openid profile read write'

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

async function exchangeCode(code, redirectUri) {
  const codeVerifier = sessionStorage.getItem('pkce_code_verifier')
  if (!codeVerifier) {
    throw new Error('未找到 code_verifier，请重新登录')
  }
  sessionStorage.removeItem('pkce_code_verifier')

  const params = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    client_id: CLIENT_ID,
    redirect_uri: redirectUri || REDIRECT_URI,
    code_verifier: codeVerifier
  })

  try {
    const { data } = await authServerClient.post('/oauth2/token', params.toString())
    const store = useOAuth2Store()
    store.setTokens(data)
    return data
  } catch (e) {
    const desc = e.response?.data?.error_description || e.response?.data?.error || '令牌交换失败'
    throw new Error(desc)
  }
}

async function refreshToken() {
  const store = useOAuth2Store()
  const rt = store.refreshToken
  if (!rt) {
    throw new Error('无 refresh_token，请重新登录')
  }

  const params = new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: rt,
    client_id: CLIENT_ID
  })

  try {
    const { data } = await authServerClient.post('/oauth2/token', params.toString())
    store.setTokens(data)
    return data
  } catch (e) {
    const desc = e.response?.data?.error_description || e.response?.data?.error || '刷新令牌失败'
    throw new Error(desc)
  }
}

function logout() {
  const store = useOAuth2Store()
  const idToken = store.idToken
  store.clear()

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
  const store = useOAuth2Store()
  if (!store.isAuthenticated) {
    store.clear()
    throw new Error('Token 已过期，请重新登录')
  }

  try {
    const { data } = await resourceServerClient.get(endpoint)
    return data
  } catch (e) {
    if (e.response?.status === 401) {
      store.clear()
      throw new Error('401: 令牌无效或已过期，请重新登录')
    }
    throw new Error(`请求失败: ${e.response?.status || e.message}`)
  }
}

const SILENT_REDIRECT_URI = `${window.location.origin}/silent-refresh.html`

function startSilentRefresh() {
  const codeVerifier = generateRandomString(32)
  const iframe = document.createElement('iframe')
  iframe.style.display = 'none'

  sha256(codeVerifier).then((codeChallenge) => {
    sessionStorage.setItem('pkce_code_verifier', codeVerifier)
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: CLIENT_ID,
      redirect_uri: SILENT_REDIRECT_URI,
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
      if (e.origin !== window.location.origin) return
      if (e.data?.type !== 'oauth2-silent-refresh') return
      clearTimeout(timer)
      document.body.removeChild(iframe)
      window.removeEventListener('message', handler)
      if (e.data.code) {
        exchangeCode(e.data.code, SILENT_REDIRECT_URI).then(resolve).catch(reject)
      } else {
        reject(new Error('静默刷新失败: ' + (e.data.error || 'login_required')))
      }
    })
  })
}

export default {
  startAuthorization,
  exchangeCode,
  refreshToken,
  logout,
  parseJwt,
  callResourceServer,
  startSilentRefresh,
  AUTH_SERVER,
  RESOURCE_SERVER
}
