import axios from 'axios'

const AUTH_SERVER = 'http://localhost:9000'
const RESOURCE_SERVER = 'http://localhost:9001'

const authServerClient = axios.create({
  baseURL: AUTH_SERVER,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
})

const resourceServerClient = axios.create({
  baseURL: RESOURCE_SERVER
})

resourceServerClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

resourceServerClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('access_token')
      sessionStorage.removeItem('refresh_token')
      sessionStorage.removeItem('id_token')
      sessionStorage.removeItem('token_expires_at')
      sessionStorage.removeItem('pkce_code_verifier')
    }
    return Promise.reject(error)
  }
)

export { authServerClient, resourceServerClient, AUTH_SERVER, RESOURCE_SERVER }
