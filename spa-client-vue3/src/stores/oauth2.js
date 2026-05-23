import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { parseJwt } from '../utils/crypto.js'

export const useOAuth2Store = defineStore('oauth2', () => {
  const accessToken = ref('')
  const idToken = ref('')
  const refreshToken = ref('')
  const expiresAtMs = ref(0)
  const claims = ref({})

  const isAuthenticated = computed(() => {
    if (!accessToken.value) return false
    if (expiresAtMs.value && Date.now() > expiresAtMs.value) {
      clear()
      return false
    }
    return true
  })

  const scopes = computed(() => {
    const scopeStr = claims.value?.scp || claims.value?.scope || ''
    return scopeStr
      ? (typeof scopeStr === 'string' ? scopeStr.split(' ').filter(Boolean) : scopeStr)
      : []
  })

  const remaining = computed(() => {
    if (!expiresAtMs.value) return 0
    return Math.max(0, expiresAtMs.value - Date.now())
  })

  function setTokens(tokenResponse) {
    accessToken.value = tokenResponse.access_token || ''
    if (tokenResponse.refresh_token) refreshToken.value = tokenResponse.refresh_token
    if (tokenResponse.id_token) idToken.value = tokenResponse.id_token
    expiresAtMs.value = Date.now() + (tokenResponse.expires_in || 0) * 1000
    claims.value = parseJwt(accessToken.value) || {}
    sessionStorage.setItem('access_token', accessToken.value)
    if (tokenResponse.refresh_token) sessionStorage.setItem('refresh_token', tokenResponse.refresh_token)
    if (tokenResponse.id_token) sessionStorage.setItem('id_token', tokenResponse.id_token)
    sessionStorage.setItem('token_expires_at', String(expiresAtMs.value))
  }

  function restore() {
    const at = sessionStorage.getItem('access_token')
    if (at) {
      accessToken.value = at
      idToken.value = sessionStorage.getItem('id_token') || ''
      refreshToken.value = sessionStorage.getItem('refresh_token') || ''
      expiresAtMs.value = Number(sessionStorage.getItem('token_expires_at')) || 0
      claims.value = parseJwt(at) || {}
    }
  }

  function clear() {
    accessToken.value = ''
    idToken.value = ''
    refreshToken.value = ''
    expiresAtMs.value = 0
    claims.value = {}
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('refresh_token')
    sessionStorage.removeItem('id_token')
    sessionStorage.removeItem('token_expires_at')
    sessionStorage.removeItem('pkce_code_verifier')
  }

  return {
    accessToken,
    idToken,
    refreshToken,
    expiresAtMs,
    claims,
    isAuthenticated,
    scopes,
    remaining,
    setTokens,
    restore,
    clear
  }
})
